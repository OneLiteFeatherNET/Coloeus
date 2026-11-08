package net.onelitefeather.coloeus.paper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.money.CurrencyUnit;
import javax.money.MonetaryAmount;
import net.onelitefeather.coloeus.client.AccountBalance;
import net.onelitefeather.coloeus.client.ColoeusClient;
import net.onelitefeather.coloeus.client.CurrencyDefinition;
import net.onelitefeather.coloeus.client.ForbiddenGrantException;
import net.onelitefeather.coloeus.client.InsufficientFundsException;
import net.onelitefeather.coloeus.client.PlayerAccount;
import net.onelitefeather.coloeus.client.PlayerUnknownException;
import net.onelitefeather.coloeus.client.Transaction;
import net.onelitefeather.coloeus.client.TransactionResult;
import org.javamoney.moneta.Money;

/**
 * An in-memory {@link ColoeusClient} for the paper tests: it books like the ledger does, refuses what the ledger would
 * refuse, and can be told to fail the next call. No network, no server.
 */
final class FakeColoeusClient implements ColoeusClient {

    private final CurrencyDefinition currency;
    private final CurrencyUnit unit;
    private final Map<UUID, BigDecimal> balances = new HashMap<>();
    private final Map<String, UUID> names = new HashMap<>();
    private final List<Transaction> transactions = new ArrayList<>();
    private final List<String> transactionKeys = new ArrayList<>();
    private final List<UUID> reports = new ArrayList<>();
    private int accountReads;
    private int nameLookups;
    private boolean granted = true;
    private RuntimeException transactFailure;
    private RuntimeException accountFailure;
    private RuntimeException reportFailure;

    FakeColoeusClient(CurrencyDefinition currency) {
        this.currency = currency;
        this.unit = currency.unit();
    }

    CurrencyDefinition currency() {
        return currency;
    }

    void setBalance(UUID player, String amount) {
        balances.put(player, new BigDecimal(amount));
    }

    BigDecimal balance(UUID player) {
        return balances.get(player);
    }

    void registerName(String name, UUID player) {
        names.put(name, player);
    }

    void revokeGrant() {
        granted = false;
    }

    void failNextTransaction(RuntimeException failure) {
        transactFailure = failure;
    }

    void failAccountReads(RuntimeException failure) {
        accountFailure = failure;
    }

    void failReports(RuntimeException failure) {
        reportFailure = failure;
    }

    List<Transaction> transactions() {
        return List.copyOf(transactions);
    }

    List<String> transactionKeys() {
        return List.copyOf(transactionKeys);
    }

    List<UUID> reports() {
        return List.copyOf(reports);
    }

    int accountReads() {
        return accountReads;
    }

    int nameLookups() {
        return nameLookups;
    }

    @Override
    public List<CurrencyDefinition> currencies(Duration timeout) {
        return List.of(currency);
    }

    @Override
    public UUID playerByName(String name, Duration timeout) {
        nameLookups++;
        UUID player = names.get(name);
        if (player == null) {
            throw new PlayerUnknownException(404, "unknown name in the fake");
        }
        return player;
    }

    @Override
    public void reportName(UUID uuid, String name, String idempotencyKey, Duration budget) {
        if (reportFailure != null) {
            throw reportFailure;
        }
        reports.add(uuid);
    }

    @Override
    public PlayerAccount playerAccount(UUID uuid, Duration timeout) {
        accountReads++;
        if (accountFailure != null) {
            throw accountFailure;
        }
        BigDecimal balance = balances.get(uuid);
        List<MonetaryAmount> amounts = balance == null ? List.of() : List.of(money(balance));
        return new PlayerAccount(accountOf(uuid), uuid, amounts);
    }

    @Override
    public List<MonetaryAmount> balances(UUID accountId, Duration timeout) {
        throw new UnsupportedOperationException("not used by the paper tests");
    }

    @Override
    public TransactionResult transact(Transaction transaction, String idempotencyKey, Duration budget) {
        transactions.add(transaction);
        transactionKeys.add(idempotencyKey);
        if (transactFailure != null) {
            throw transactFailure;
        }
        if (!granted) {
            throw new ForbiddenGrantException(403, "no grant in the fake");
        }
        boolean mint = transaction.kind() == Transaction.Kind.MINT;
        UUID player = mint ? transaction.to() : transaction.from();
        BigDecimal amount = transaction.amount().getNumber().numberValue(BigDecimal.class);
        BigDecimal current = balances.getOrDefault(player, BigDecimal.ZERO);
        BigDecimal next = mint ? current.add(amount) : current.subtract(amount);
        if (next.signum() < 0) {
            throw new InsufficientFundsException(422, "the balance would fall below zero in the fake");
        }
        balances.put(player, next);
        return new TransactionResult(UUID.randomUUID(), List.of(new AccountBalance(accountOf(player), money(next))));
    }

    private MonetaryAmount money(BigDecimal value) {
        return Money.of(value, unit);
    }

    /** The account id of a player: stable for the player, and not the player's UUID, as in the ledger. */
    private static UUID accountOf(UUID player) {
        return UUID.nameUUIDFromBytes(("account:" + player).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
