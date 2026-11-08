package net.onelitefeather.coloeus.ledger;

import jakarta.inject.Singleton;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import javax.money.CurrencyUnit;
import javax.money.MonetaryAmount;
import net.onelitefeather.coloeus.account.AccountKind;
import net.onelitefeather.coloeus.account.AccountRepository;
import net.onelitefeather.coloeus.account.AccountService;
import net.onelitefeather.coloeus.auth.ActorCheck;
import net.onelitefeather.coloeus.auth.Caller;
import net.onelitefeather.coloeus.auth.GrantService;
import net.onelitefeather.coloeus.auth.Permission;
import net.onelitefeather.coloeus.auth.Roles;
import net.onelitefeather.coloeus.currency.CurrencyService;
import net.onelitefeather.coloeus.money.AmountJson;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;
import net.onelitefeather.coloeus.request.RequestValues;
import org.javamoney.moneta.Money;

/**
 * Books a validated transaction inside the caller's database transaction (design decision 2).
 * <ol>
 *   <li>Resolve the accounts, creating player and system accounts when they are missing.</li>
 *   <li>Ensure a balance row for every affected account and currency, in one fixed order.</li>
 *   <li>Lock the player balances in that order and reject any that would fall below zero.</li>
 *   <li>Update the balances: locked rows absolutely, source, sink, fee and admin rows by increment.</li>
 *   <li>Insert the transaction and its postings. The deferred trigger checks the zero sum at commit.</li>
 * </ol>
 * Every row is touched in the order of {@code (account id, currency)}; the unlocked rows come after all locks, so
 * no two bookings can wait on each other in a cycle.
 */
@Singleton
public class BookingService {

    private final AccountRepository accounts;
    private final JournalRepository journal;
    private final CurrencyService currencies;
    private final GrantService grants;

    public BookingService(AccountRepository accounts, JournalRepository journal, CurrencyService currencies,
                          GrantService grants) {
        this.accounts = accounts;
        this.journal = journal;
        this.currencies = currencies;
        this.grants = grants;
    }

    /** The row of one account and currency that a booking touches. */
    private record Slot(UUID accountId, AccountKind kind, String currency) {
    }

    private static final Comparator<Slot> ORDER =
            Comparator.comparing(Slot::accountId).thenComparing(Slot::currency);

    /**
     * A leg with its account resolved; {@code amount} is signed. {@code owner} is the player UUID of a PLAYER account
     * and null for every other kind.
     */
    private record ResolvedLeg(UUID accountId, AccountKind kind, UUID owner, MonetaryAmount amount) {
        String currency() {
            return amount.getCurrency().getCurrencyCode();
        }

        boolean isDebit() {
            return amount.isNegative();
        }
    }

    /**
     * Books a validated command inside the caller's database transaction and returns the answer: the postings and
     * the balances of the player accounts it touched. The caller must already be registered (the transaction
     * endpoint checks that before it takes the idempotency key).
     *
     * @throws ProblemException when the caller may not book the command (see {@link #authorize})
     */
    public TransactionResponse book(Connection connection, Caller caller, TransactionCommand command, Instant now,
                             String metadataJson) throws SQLException {
        List<ResolvedLeg> legs = resolveLegs(connection, caller.clientId(), command, now);
        authorize(connection, caller, command, legs);
        return apply(connection, caller.clientId(), command, legs, now, metadataJson);
    }

    /**
     * Books a staff money write (RESET or ADJUST) inside the caller's database transaction, without a client and
     * without a grant. The staff API has checked the session, the role and the CSRF token before it calls this.
     *
     * @throws IllegalArgumentException when the command is not a staff kind
     */
    public TransactionResponse bookStaff(Connection connection, TransactionCommand command, Instant now,
                                         String metadataJson) throws SQLException {
        if (command.kind() != TransactionKind.RESET && command.kind() != TransactionKind.ADJUST) {
            throw new IllegalArgumentException("only RESET and ADJUST are staff transactions");
        }
        List<ResolvedLeg> legs = resolveLegs(connection, null, command, now);
        return apply(connection, null, command, legs, now, metadataJson);
    }

    /**
     * Creates the balance row of a player account in a currency when it is missing and locks it until the transaction
     * ends. A reset reads the balance it books under this lock.
     */
    public Money lockBalance(Connection connection, UUID accountId, String currency, Instant now) throws SQLException {
        journal.ensureBalance(connection, accountId, currency, now);
        return journal.lockBalance(connection, accountId, currency, unit(currency));
    }

    private List<ResolvedLeg> resolveLegs(Connection connection, UUID clientId, TransactionCommand command, Instant now)
            throws SQLException {
        List<ResolvedLeg> legs = new ArrayList<>();
        for (Leg leg : command.legs()) {
            legs.add(resolve(connection, clientId, command.kind(), leg, now));
        }
        if (command.kind() == TransactionKind.TRANSFER && legs.get(0).accountId().equals(legs.get(1).accountId())) {
            throw RequestValues.invalid("from and to must be different accounts");
        }
        return legs;
    }

    /** Locks, checks and writes the resolved legs, then journals them. {@code clientId} is null for staff writes. */
    private TransactionResponse apply(Connection connection, UUID clientId, TransactionCommand command,
                                      List<ResolvedLeg> legs, Instant now, String metadataJson) throws SQLException {
        UUID transactionId = UUID.randomUUID();

        Map<Slot, Money> deltas = new TreeMap<>(ORDER);
        for (ResolvedLeg leg : legs) {
            Slot slot = new Slot(leg.accountId(), leg.kind(), leg.currency());
            deltas.merge(slot, Money.from(leg.amount()), Money::add);
        }

        for (Slot slot : deltas.keySet()) {
            journal.ensureBalance(connection, slot.accountId(), slot.currency(), now);
        }

        Map<Slot, Money> locked = new TreeMap<>(ORDER);
        for (Map.Entry<Slot, Money> entry : deltas.entrySet()) {
            Slot slot = entry.getKey();
            if (!isLocked(slot.kind())) {
                continue;
            }
            CurrencyUnit unit = unit(slot.currency());
            Money current = journal.lockBalance(connection, slot.accountId(), slot.currency(), unit);
            Money next = current.add(entry.getValue());
            if (slot.kind() == AccountKind.PLAYER && next.isNegative()) {
                throw ProblemException.of(ProblemCode.INSUFFICIENT_FUNDS,
                        "the balance of account " + slot.accountId() + " in " + slot.currency()
                                + " would fall below zero");
            }
            locked.put(slot, next);
        }

        for (Map.Entry<Slot, Money> entry : deltas.entrySet()) {
            Slot slot = entry.getKey();
            if (locked.containsKey(slot)) {
                journal.setBalance(connection, slot.accountId(), slot.currency(), locked.get(slot), now);
            } else {
                journal.addToBalance(connection, slot.accountId(), slot.currency(), entry.getValue(), now);
            }
        }

        journal.insertTransaction(connection, transactionId, command, clientId, metadataJson, now);
        List<TransactionResponse.PostingLine> postings = new ArrayList<>();
        for (ResolvedLeg leg : legs) {
            journal.insertPosting(connection, transactionId, leg.accountId(), leg.amount(), now);
            postings.add(new TransactionResponse.PostingLine(leg.accountId(), AmountJson.of(leg.amount())));
        }

        List<TransactionResponse.BalanceLine> balances = new ArrayList<>();
        for (Map.Entry<Slot, Money> entry : locked.entrySet()) {
            if (entry.getKey().kind() == AccountKind.PLAYER) {
                balances.add(new TransactionResponse.BalanceLine(
                        entry.getKey().accountId(), AmountJson.of(entry.getValue())));
            }
        }

        return new TransactionResponse(
                transactionId,
                command.kind().name(),
                now,
                command.reason(),
                command.actor(),
                command.externalReference(),
                command.metadata(),
                postings,
                balances);
    }

    /**
     * The rules of who may book what (service-auth spec, design decision 7):
     * <ul>
     *   <li>MINT debits the client's SOURCE and needs the {@link Permission#MINT} grant for the currency;
     *       BURN credits the client's SINK and needs {@link Permission#BURN}. Admins need the grant too.</li>
     *   <li>TRANSFER and BATCH debiting a player account need {@link Permission#TRANSFER_ON_BEHALF} for the currency
     *       and the player as {@code actor}; an admin needs neither ({@link ActorCheck}).</li>
     *   <li>TRANSFER and BATCH debiting any other account (FEE, ADMIN_*, BANK) need the admin role.</li>
     * </ul>
     */
    private void authorize(Connection connection, Caller caller, TransactionCommand command, List<ResolvedLeg> legs)
            throws SQLException {
        switch (command.kind()) {
            case MINT -> grants.require(connection, caller, legs.get(0).currency(), Permission.MINT);
            case BURN -> grants.require(connection, caller, legs.get(0).currency(), Permission.BURN);
            case RESET, ADJUST -> {
                // Staff money writes are authorized by the staff API before bookStaff runs; no grant applies.
            }
            case TRANSFER, BATCH -> {
                for (ResolvedLeg leg : legs) {
                    if (!leg.isDebit()) {
                        continue;
                    }
                    if (leg.kind() == AccountKind.PLAYER) {
                        if (!caller.admin()) {
                            grants.require(connection, caller, leg.currency(), Permission.TRANSFER_ON_BEHALF);
                        }
                        ActorCheck.require(caller, leg.owner(), command.actor());
                    } else if (!caller.admin()) {
                        throw ProblemException.of(ProblemCode.FORBIDDEN_ROLE,
                                "debiting a " + leg.kind() + " account requires the " + Roles.ADMIN + " role");
                    }
                }
            }
        }
    }

    private ResolvedLeg resolve(Connection connection, UUID clientId, TransactionKind kind, Leg leg, Instant now)
            throws SQLException {
        return switch (leg.account()) {
            case AccountRef.ById byId -> resolveById(connection, kind, byId.id(), leg.amount());
            case AccountRef.Player player -> new ResolvedLeg(
                    accounts.ensurePlayer(connection, player.owner(), now), AccountKind.PLAYER, player.owner(),
                    leg.amount());
            case AccountRef.ClientSystem system -> new ResolvedLeg(
                    accounts.ensureClientSystem(connection, clientId, system.kind(), now), system.kind(), null,
                    leg.amount());
            case AccountRef.CurrencySystem system -> new ResolvedLeg(
                    accounts.ensureCurrencySystem(connection, system.kind(), system.currency(), now),
                    system.kind(), null, leg.amount());
        };
    }

    private ResolvedLeg resolveById(Connection connection, TransactionKind kind, UUID id, MonetaryAmount amount)
            throws SQLException {
        AccountRepository.AccountRow row = accounts.find(connection, id)
                .orElseThrow(() -> AccountService.notFound(id));
        if (row.kind() == AccountKind.SOURCE || row.kind() == AccountKind.SINK) {
            throw ProblemException.of(ProblemCode.ACCOUNT_FORBIDDEN,
                    "SOURCE and SINK accounts are used through MINT and BURN only");
        }
        if ((kind == TransactionKind.MINT || kind == TransactionKind.BURN) && row.kind() != AccountKind.PLAYER) {
            throw RequestValues.invalid("MINT and BURN move money to or from player accounts");
        }
        return new ResolvedLeg(row.id(), row.kind(), row.owner(), amount);
    }

    /** Locked rows are the ones whose balance gates a booking. */
    private static boolean isLocked(AccountKind kind) {
        return kind == AccountKind.PLAYER || kind == AccountKind.BANK;
    }

    private CurrencyUnit unit(String code) {
        return currencies.lookup(code).orElseThrow(() ->
                new IllegalStateException("currency " + code + " is stored but not served"));
    }

}
