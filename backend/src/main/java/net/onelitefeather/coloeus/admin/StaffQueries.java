package net.onelitefeather.coloeus.admin;

import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.money.CurrencyUnit;
import net.onelitefeather.coloeus.account.AccountRepository;
import net.onelitefeather.coloeus.account.PostingCursor;
import net.onelitefeather.coloeus.currency.CurrencyService;
import net.onelitefeather.coloeus.money.AmountColumns;
import net.onelitefeather.coloeus.money.AmountJson;
import org.javamoney.moneta.Money;

/**
 * The SQL of the staff reads on accounts, names, postings and the staff action log. Every method runs on the
 * connection of the caller's transaction. Amounts are read as text and converted by {@link AmountColumns}.
 */
@Singleton
public class StaffQueries {

    private final CurrencyService currencies;
    private final JsonMapper json;

    public StaffQueries(CurrencyService currencies, JsonMapper json) {
        this.currencies = currencies;
        this.json = json;
    }

    /** A register row of a name: the UUID, the name it held and when it was last seen. */
    public record NameRow(UUID uuid, String name, Instant lastSeen) {
    }

    /** The name of a registered client. */
    public record ClientName(UUID clientId, String displayName) {
    }

    /** Register rows of a name (case-insensitive), one per UUID, the most recently seen first, at most {@code limit}. */
    public List<NameRow> namesHeldBy(Connection connection, String name, int limit) throws SQLException {
        String sql = "SELECT uuid, name, last_seen FROM (SELECT DISTINCT ON (uuid) uuid, name, last_seen "
                + "FROM player_name WHERE lower(name) = lower(?) ORDER BY uuid, last_seen DESC) held "
                + "ORDER BY last_seen DESC, uuid LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setInt(2, limit);
            return nameRows(statement);
        }
    }

    /** The newest register row of a UUID, if the register knows it. */
    public Optional<NameRow> newestNameOf(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT uuid, name, last_seen FROM player_name WHERE uuid = ? ORDER BY last_seen DESC, name LIMIT 1")) {
            statement.setObject(1, uuid);
            List<NameRow> rows = nameRows(statement);
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
        }
    }

    /** The PLAYER account of a UUID, if it exists. */
    public Optional<UUID> playerAccountOf(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM account WHERE kind = 'PLAYER' AND owner_uuid = ?")) {
            statement.setObject(1, uuid);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(rows.getObject(1, UUID.class)) : Optional.empty();
            }
        }
    }

    /** The name of a client, if it is registered. */
    public Optional<ClientName> clientName(Connection connection, UUID clientId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT display_name FROM client WHERE client_id = ?")) {
            statement.setObject(1, clientId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(new ClientName(clientId, rows.getString(1))) : Optional.empty();
            }
        }
    }

    /**
     * One page of an account's postings, newest first, after {@code after} when given. The staff name is the metadata
     * {@code staff_name} of the transaction.
     */
    public List<StaffViews.StaffPosting> postings(Connection connection, UUID accountId, PostingCursor after, int limit)
            throws SQLException {
        String sql = "SELECT p.id, p.transaction_id, p.currency, p.amount::text AS amount, p.created_at, t.kind, "
                + "t.reason, t.actor, t.metadata ->> 'staff_name' AS staff_name, "
                + "ARRAY(SELECT DISTINCT o.account_id::text FROM posting o "
                + "WHERE o.transaction_id = p.transaction_id AND o.account_id <> p.account_id) AS counter_accounts "
                + "FROM posting p JOIN transaction t ON t.id = p.transaction_id WHERE p.account_id = ? "
                + (after == null ? "" : "AND (p.created_at, p.id) < (?, ?) ")
                + "ORDER BY p.created_at DESC, p.id DESC LIMIT ?";
        List<StaffViews.StaffPosting> result = new ArrayList<>();
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
                    CurrencyUnit unit = currencyUnit(currency);
                    Money amount = AmountColumns.fromColumn(rows.getString("amount"), unit);
                    result.add(new StaffViews.StaffPosting(
                            rows.getObject("id", UUID.class).toString(),
                            rows.getObject("transaction_id", UUID.class).toString(),
                            rows.getString("kind"),
                            rows.getString("reason"),
                            rows.getString("actor"),
                            rows.getString("staff_name"),
                            currency,
                            AmountJson.of(amount).amount(),
                            counterAccounts(rows.getArray("counter_accounts")),
                            rows.getTimestamp("created_at").toInstant()));
                }
            }
        }
        return result;
    }

    /** The staff action log, newest first, after {@code after} when given. */
    public List<StaffViews.StaffAction> staffActions(Connection connection, PostingCursor after, int limit)
            throws SQLException {
        String sql = "SELECT id, actor_oid, actor_name, action, target, transaction_id, detail::text AS detail, "
                + "created_at FROM staff_action "
                + (after == null ? "" : "WHERE (created_at, id) < (?, ?) ")
                + "ORDER BY created_at DESC, id DESC LIMIT ?";
        List<StaffViews.StaffAction> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (after != null) {
                statement.setTimestamp(index++, Timestamp.from(after.createdAt()));
                statement.setObject(index++, after.postingId());
            }
            statement.setInt(index, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    UUID transactionId = rows.getObject("transaction_id", UUID.class);
                    result.add(new StaffViews.StaffAction(
                            rows.getObject("id", UUID.class).toString(),
                            rows.getString("actor_oid"),
                            rows.getString("actor_name"),
                            rows.getString("action"),
                            rows.getString("target"),
                            transactionId == null ? null : transactionId.toString(),
                            detail(rows.getString("detail")),
                            rows.getTimestamp("created_at").toInstant()));
                }
            }
        }
        return result;
    }

    CurrencyUnit currencyUnit(String code) {
        return currencies.lookup(code).orElseThrow(() ->
                new IllegalStateException("currency " + code + " is stored but not served"));
    }

    private static List<NameRow> nameRows(PreparedStatement statement) throws SQLException {
        List<NameRow> rows = new ArrayList<>();
        try (ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                rows.add(new NameRow(
                        result.getObject("uuid", UUID.class),
                        result.getString("name"),
                        result.getTimestamp("last_seen").toInstant()));
            }
        }
        return rows;
    }

    private static List<String> counterAccounts(java.sql.Array array) throws SQLException {
        List<String> result = new ArrayList<>();
        for (String id : (String[]) array.getArray()) {
            result.add(id);
        }
        return result;
    }

    /** The stored detail of a staff action: a JSON object whose values are strings. */
    private Map<String, String> detail(String text) {
        if (text == null) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = json.readValue(text, Argument.mapOf(String.class, Object.class));
            Map<String, String> result = new LinkedHashMap<>();
            parsed.forEach((key, value) -> result.put(key, String.valueOf(value)));
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("the stored detail of a staff action is not JSON", e);
        }
    }
}
