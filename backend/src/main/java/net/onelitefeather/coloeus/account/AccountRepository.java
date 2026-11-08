package net.onelitefeather.coloeus.account;

import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.money.CurrencyUnit;
import javax.money.MonetaryAmount;
import net.onelitefeather.coloeus.currency.CurrencyService;
import net.onelitefeather.coloeus.money.AmountColumns;
import org.javamoney.moneta.Money;

/**
 * SQL for accounts and balance reads. Every method runs on the connection of the caller's transaction.
 */
@Singleton
public class AccountRepository {

    private final CurrencyService currencies;

    public AccountRepository(CurrencyService currencies) {
        this.currencies = currencies;
    }

    /** Converts a stored amount to money in its currency; a stored value is read, never rounded. */
    private Money money(String currency, BigDecimal stored) {
        CurrencyUnit unit = currencies.lookup(currency).orElseThrow(() ->
                new IllegalStateException("currency " + currency + " is stored but not served"));
        return AmountColumns.fromColumn(stored, unit);
    }

    /** An account row as stored. Player accounts have an owner, SOURCE and SINK a client, FEE and ADMIN_* a currency. */
    public record AccountRow(UUID id, AccountKind kind, UUID owner, UUID clientId, String currency) {
    }

    /** A balance of one currency; zero when no balance row exists yet. */
    record BalanceRow(String currency, MonetaryAmount amount) {
    }

    /** The id and time of an account's newest posting. */
    record PostingPosition(UUID id, Instant createdAt) {
    }

    /** A posting of an account joined with its transaction; the amount is signed. */
    record PostingRow(UUID id, UUID transactionId, String currency, MonetaryAmount amount, Instant createdAt,
                      String kind, String reason, String actor, List<UUID> counterAccounts) {
    }

    /** Creates the player account of a UUID if absent and returns its id. Concurrent calls create one account. */
    public UUID ensurePlayer(Connection connection, UUID owner, Instant now) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO account (id, kind, owner_uuid, created_at) VALUES (?, 'PLAYER', ?, ?) ON CONFLICT DO NOTHING")) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, owner);
            insert.setTimestamp(3, Timestamp.from(now));
            insert.executeUpdate();
        }
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id FROM account WHERE kind = 'PLAYER' AND owner_uuid = ?")) {
            select.setObject(1, owner);
            try (ResultSet rows = select.executeQuery()) {
                rows.next();
                return rows.getObject(1, UUID.class);
            }
        }
    }

    /** Creates the SOURCE or SINK account of a client if absent and returns its id. */
    public UUID ensureClientSystem(Connection connection, UUID clientId, AccountKind kind, Instant now) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO account (id, kind, client_id, created_at) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING")) {
            insert.setObject(1, UUID.randomUUID());
            insert.setString(2, kind.name());
            insert.setObject(3, clientId);
            insert.setTimestamp(4, Timestamp.from(now));
            insert.executeUpdate();
        }
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id FROM account WHERE client_id = ? AND kind = ?")) {
            select.setObject(1, clientId);
            select.setString(2, kind.name());
            try (ResultSet rows = select.executeQuery()) {
                rows.next();
                return rows.getObject(1, UUID.class);
            }
        }
    }

    /** Creates the FEE or ADMIN_* account of a currency if absent and returns its id. */
    public UUID ensureCurrencySystem(Connection connection, AccountKind kind, String currency, Instant now) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO account (id, kind, currency, created_at) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING")) {
            insert.setObject(1, UUID.randomUUID());
            insert.setString(2, kind.name());
            insert.setString(3, currency);
            insert.setTimestamp(4, Timestamp.from(now));
            insert.executeUpdate();
        }
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id FROM account WHERE currency = ? AND kind = ?")) {
            select.setString(1, currency);
            select.setString(2, kind.name());
            try (ResultSet rows = select.executeQuery()) {
                rows.next();
                return rows.getObject(1, UUID.class);
            }
        }
    }

    public Optional<AccountRow> find(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, kind, owner_uuid, client_id, currency FROM account WHERE id = ?")) {
            statement.setObject(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(new AccountRow(
                        rows.getObject("id", UUID.class),
                        AccountKind.valueOf(rows.getString("kind")),
                        rows.getObject("owner_uuid", UUID.class),
                        rows.getObject("client_id", UUID.class),
                        rows.getString("currency")));
            }
        }
    }

    /** One row per currency the service defines, ordered by code; the amount is null without a balance row. */
    List<BalanceRow> balances(Connection connection, UUID accountId) throws SQLException {
        List<BalanceRow> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT c.code, b.amount FROM currency c "
                        + "LEFT JOIN balance b ON b.currency = c.code AND b.account_id = ? ORDER BY c.code")) {
            statement.setObject(1, accountId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String code = rows.getString("code");
                    BigDecimal stored = rows.getBigDecimal("amount");
                    result.add(new BalanceRow(code, money(code, stored == null ? BigDecimal.ZERO : stored)));
                }
            }
        }
        return result;
    }

    /**
     * Up to {@code limit} postings of an account, newest first, after {@code after} when it is set. The keyset is
     * {@code (created_at, id)}, so postings of one transaction that share a time keep a stable order.
     */
    List<PostingRow> postings(Connection connection, UUID accountId, PostingCursor after, int limit)
            throws SQLException {
        String sql = "SELECT p.id, p.transaction_id, p.currency, p.amount, p.created_at, t.kind, t.reason, t.actor, "
                + "ARRAY(SELECT DISTINCT o.account_id::text FROM posting o "
                + "WHERE o.transaction_id = p.transaction_id AND o.account_id <> p.account_id) AS counter_accounts "
                + "FROM posting p JOIN transaction t ON t.id = p.transaction_id WHERE p.account_id = ? "
                + (after == null ? "" : "AND (p.created_at, p.id) < (?, ?) ")
                + "ORDER BY p.created_at DESC, p.id DESC LIMIT ?";
        List<PostingRow> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setObject(index++, accountId);
            if (after != null) {
                statement.setTimestamp(index++, Timestamp.from(after.createdAt()));
                statement.setObject(index++, after.postingId());
            }
            statement.setInt(index, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String currency = rows.getString("currency");
                    result.add(new PostingRow(
                            rows.getObject("id", UUID.class),
                            rows.getObject("transaction_id", UUID.class),
                            currency,
                            money(currency, rows.getBigDecimal("amount")),
                            rows.getTimestamp("created_at").toInstant(),
                            rows.getString("kind"),
                            rows.getString("reason"),
                            rows.getString("actor"),
                            counterAccounts(rows.getArray("counter_accounts"))));
                }
            }
        }
        return result;
    }

    private static List<UUID> counterAccounts(java.sql.Array array) throws SQLException {
        List<UUID> result = new ArrayList<>();
        for (String id : (String[]) array.getArray()) {
            result.add(UUID.fromString(id));
        }
        return result;
    }

    Optional<PostingPosition> latestPosting(Connection connection, UUID accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, created_at FROM posting WHERE account_id = ? ORDER BY created_at DESC, id DESC LIMIT 1")) {
            statement.setObject(1, accountId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PostingPosition(
                        rows.getObject("id", UUID.class),
                        rows.getTimestamp("created_at").toInstant()));
            }
        }
    }
}
