package net.onelitefeather.coloeus.admin;

import io.micronaut.json.tree.JsonNode;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.money.MonetaryAmount;
import net.onelitefeather.coloeus.account.AccountKind;
import net.onelitefeather.coloeus.account.AccountRepository;
import net.onelitefeather.coloeus.account.AccountService;
import net.onelitefeather.coloeus.currency.Currency;
import net.onelitefeather.coloeus.currency.CurrencyService;
import net.onelitefeather.coloeus.ledger.AccountRef;
import net.onelitefeather.coloeus.ledger.BookingService;
import net.onelitefeather.coloeus.ledger.IdempotentWriter;
import net.onelitefeather.coloeus.ledger.Leg;
import net.onelitefeather.coloeus.ledger.TransactionCommand;
import net.onelitefeather.coloeus.ledger.TransactionKind;
import net.onelitefeather.coloeus.ledger.TransactionResponse;
import net.onelitefeather.coloeus.money.AmountJson;
import net.onelitefeather.coloeus.persistence.JdbcTransactions;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;
import net.onelitefeather.coloeus.request.RequestValues;
import java.sql.Connection;
import java.time.Instant;

/**
 * The money writes of the staff API: reset and adjust of a player balance (staff-operations and staff-api specs). Each is
 * one journal transaction of kind RESET or ADJUST with the caller's oid as actor, booked against the currency's
 * ADMIN_SOURCE or ADMIN_SINK only, plus one {@code staff_action} row, in one database transaction with its key.
 */
@Singleton
public class StaffMoneyService {

    private final JdbcTransactions transactions;
    private final BookingService booking;
    private final AccountRepository accounts;
    private final CurrencyService currencies;
    private final StaffAudit audit;
    private final StaffWrites writes;

    public StaffMoneyService(JdbcTransactions transactions, BookingService booking, AccountRepository accounts,
                             CurrencyService currencies, StaffAudit audit, StaffWrites writes) {
        this.transactions = transactions;
        this.booking = booking;
        this.accounts = accounts;
        this.currencies = currencies;
        this.audit = audit;
        this.writes = writes;
    }

    /**
     * Books the whole positive balance of the player's account in one currency, or in every currency with a positive
     * balance. Nothing to book is 409 {@code NOTHING_TO_RESET}, and nothing is written.
     *
     * @return the JSON of the {@code StaffBooking} answer
     */
    public String reset(StaffActor actor, UUID accountId, StaffBodies.Reset body, String key, String requestHash) {
        String reason = StaffWrites.reason(body.reason());
        String currency = body.currency();
        if (currency != null && currencies.lookup(currency).isEmpty()) {
            throw ProblemException.of(ProblemCode.CURRENCY_UNKNOWN, "currency " + currency + " is not defined");
        }
        requirePlayer(accountId);
        List<String> codes = currency == null
                ? currencies.list().stream().map(Currency::code).toList()
                : List.of(currency);
        Map<String, String> detail = Map.of("reason", reason, "currency", currency == null ? "ALL" : currency);
        return writes.run(actor, key, requestHash, (connection, now) -> {
            List<Leg> legs = new ArrayList<>();
            for (String code : codes) {
                MonetaryAmount balance = booking.lockBalance(connection, accountId, code, now);
                if (balance.isPositive()) {
                    legs.add(new Leg(new AccountRef.ById(accountId), balance.negate()));
                    legs.add(new Leg(new AccountRef.CurrencySystem(AccountKind.ADMIN_SINK, code), balance));
                }
            }
            if (legs.isEmpty()) {
                throw ProblemException.of(ProblemCode.NOTHING_TO_RESET,
                        "account " + accountId + " holds no positive balance to reset");
            }
            return book(connection, actor, TransactionKind.RESET, accountId, legs, reason, now, detail);
        });
    }

    /**
     * Credits or debits one amount of a player's account. A debit below zero is 422 {@code INSUFFICIENT_FUNDS}; the
     * amount is validated and never rounded.
     *
     * @return the JSON of the {@code StaffBooking} answer
     */
    public String adjust(StaffActor actor, UUID accountId, StaffBodies.Adjust body, String key, String requestHash) {
        String reason = StaffWrites.reason(body.reason());
        Direction direction = Direction.parse(body.direction());
        if (body.currency() == null || body.currency().isBlank()) {
            throw RequestValues.invalid("currency is required");
        }
        String code = body.currency();
        if (currencies.lookup(code).isEmpty()) {
            throw ProblemException.of(ProblemCode.CURRENCY_UNKNOWN, "currency " + code + " is not defined");
        }
        MonetaryAmount amount = new AmountJson(body.amount(), code).toMoney(currencies::lookup);
        requirePlayer(accountId);
        Map<String, String> detail = Map.of("reason", reason, "currency", code, "direction", direction.name(),
                "amount", body.amount());
        return writes.run(actor, key, requestHash, (connection, now) -> {
            List<Leg> legs = direction == Direction.CREDIT
                    ? List.of(new Leg(new AccountRef.CurrencySystem(AccountKind.ADMIN_SOURCE, code), amount.negate()),
                    new Leg(new AccountRef.ById(accountId), amount))
                    : List.of(new Leg(new AccountRef.ById(accountId), amount.negate()),
                    new Leg(new AccountRef.CurrencySystem(AccountKind.ADMIN_SINK, code), amount));
            return book(connection, actor, TransactionKind.ADJUST, accountId, legs, reason, now, detail);
        });
    }

    private IdempotentWriter.Outcome book(Connection connection, StaffActor actor, TransactionKind kind, UUID accountId,
                                     List<Leg> legs, String reason, Instant now, Map<String, String> detail)
            throws java.sql.SQLException {
        Map<String, String> metadata = actor.displayName() == null
                ? Map.of() : Map.of("staff_name", actor.displayName());
        TransactionCommand command = new TransactionCommand(kind, legs, reason, actor.oid(), null, metadata);
        TransactionResponse booked = booking.bookStaff(connection, command, now, writes.json(command.metadata()));
        audit.record(connection, actor, kind == TransactionKind.RESET ? StaffAudit.StaffAction.RESET
                        : StaffAudit.StaffAction.ADJUST, accountId.toString(), booked.id(), detail, now);
        return new IdempotentWriter.Outcome(booked.id(), writes.json(answer(booked, accountId, kind)));
    }

    /** The movements of the player's account in the booked transaction. */
    private static StaffViews.StaffBooking answer(TransactionResponse booked, UUID accountId, TransactionKind kind) {
        List<AmountJson> movements = booked.postings().stream()
                .filter(posting -> posting.account().equals(accountId))
                .map(TransactionResponse.PostingLine::amount)
                .toList();
        return new StaffViews.StaffBooking(booked.id().toString(), kind.name(), accountId.toString(),
                booked.createdAt(), movements);
    }

    /** The account must exist and be a player account. Nothing is created. */
    private void requirePlayer(UUID accountId) {
        AccountRepository.AccountRow account = transactions.read(connection -> accounts.find(connection, accountId))
                .orElseThrow(() -> AccountService.notFound(accountId));
        if (account.kind() != AccountKind.PLAYER) {
            throw RequestValues.invalid("only player accounts can be reset or adjusted");
        }
    }

    /** The direction of an adjust. */
    enum Direction {
        CREDIT,
        DEBIT;

        static Direction parse(String value) {
            if ("CREDIT".equals(value) || "DEBIT".equals(value)) {
                return valueOf(value);
            }
            throw RequestValues.invalid("direction must be CREDIT or DEBIT");
        }
    }
}
