package net.onelitefeather.coloeus.currency;

import jakarta.inject.Singleton;

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

/**
 * SQL for the {@code currency} table. Every method runs on the connection of the caller's transaction.
 */
@Singleton
public class CurrencyRepository {

    private static final String COLUMNS =
            "code, name_singular, name_plural, symbol, fraction_digits, is_default, created_at, updated_at";

    /** Transaction-scoped advisory lock that serialises changes which touch the default currency. */
    static final long CURRENCY_WRITE_LOCK = 727_001L;

    void lockCurrencyWrites(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
            statement.setLong(1, CURRENCY_WRITE_LOCK);
            statement.execute();
        }
    }

    long count(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM currency");
             ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getLong(1);
        }
    }

    void insert(Connection connection, Currency currency) throws SQLException {
        String sql = "INSERT INTO currency (id, " + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, currency.code());
            statement.setString(3, currency.nameSingular());
            statement.setString(4, currency.namePlural());
            statement.setString(5, currency.symbol());
            statement.setShort(6, (short) currency.fractionDigits());
            statement.setBoolean(7, currency.defaultCurrency());
            statement.setTimestamp(8, Timestamp.from(currency.createdAt()));
            statement.setTimestamp(9, Timestamp.from(currency.updatedAt()));
            statement.executeUpdate();
        }
    }

    /** Finds a currency by code; with {@code forUpdate} the row stays locked until the transaction ends. */
    Optional<Currency> findByCode(Connection connection, String code, boolean forUpdate) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM currency WHERE code = ?" + (forUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    List<Currency> findAll(Connection connection) throws SQLException {
        List<Currency> currencies = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM currency ORDER BY code");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                currencies.add(map(rows));
            }
        }
        return currencies;
    }

    void updateNames(Connection connection, String code, String nameSingular, String namePlural,
                     String symbol, Instant updatedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE currency SET name_singular = ?, name_plural = ?, symbol = ?, updated_at = ? WHERE code = ?")) {
            statement.setString(1, nameSingular);
            statement.setString(2, namePlural);
            statement.setString(3, symbol);
            statement.setTimestamp(4, Timestamp.from(updatedAt));
            statement.setString(5, code);
            statement.executeUpdate();
        }
    }

    void clearDefaultExcept(Connection connection, String code, Instant updatedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE currency SET is_default = FALSE, updated_at = ? WHERE is_default AND code <> ?")) {
            statement.setTimestamp(1, Timestamp.from(updatedAt));
            statement.setString(2, code);
            statement.executeUpdate();
        }
    }

    void markDefault(Connection connection, String code, Instant updatedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE currency SET is_default = TRUE, updated_at = ? WHERE code = ?")) {
            statement.setTimestamp(1, Timestamp.from(updatedAt));
            statement.setString(2, code);
            statement.executeUpdate();
        }
    }

    private static Currency map(ResultSet rows) throws SQLException {
        return new Currency(
                rows.getString("code"),
                rows.getString("name_singular"),
                rows.getString("name_plural"),
                rows.getString("symbol"),
                rows.getShort("fraction_digits"),
                rows.getBoolean("is_default"),
                rows.getTimestamp("created_at").toInstant(),
                rows.getTimestamp("updated_at").toInstant());
    }
}
