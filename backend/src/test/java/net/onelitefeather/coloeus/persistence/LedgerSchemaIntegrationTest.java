package net.onelitefeather.coloeus.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import net.onelitefeather.coloeus.support.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The journal invariants live in the database: append-only tables and the deferred zero-sum check.
 */
@Tag("integration")
class LedgerSchemaIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private static PostgreSQLContainer postgres;

    private Connection connection;

    @BeforeAll
    static void startDatabase() {
        postgres = PostgresTestContainer.start();
    }

    @AfterAll
    static void stopDatabase() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void freshSchema() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        connection.setAutoCommit(false);
        insertCurrency("COINS", false);
    }

    @AfterEach
    void closeConnection() throws SQLException {
        connection.close();
    }

    @Test
    void balancedTransactionCommits() throws SQLException {
        UUID account = insertPlayerAccount();
        UUID other = insertPlayerAccount();
        UUID transaction = insertTransaction();
        insertPosting(transaction, account, "1250000000");
        insertPosting(transaction, other, "-1250000000");

        assertDoesNotThrow(connection::commit, "a transaction whose postings sum to zero must commit");
    }

    @Test
    void unbalancedTransactionIsRejectedAtCommit() throws SQLException {
        UUID account = insertPlayerAccount();
        UUID other = insertPlayerAccount();
        UUID transaction = insertTransaction();
        insertPosting(transaction, account, "1000000000");
        insertPosting(transaction, other, "-500000000");

        SQLException failure = assertThrows(SQLException.class, connection::commit,
                "postings that do not sum to zero per currency must be rejected at commit");

        assertTrue(failure.getMessage().contains("do not sum to zero"),
                "the rejection must name the zero-sum rule, was: " + failure.getMessage());
    }

    @Test
    void transactionWithoutPostingsIsRejectedAtCommit() throws SQLException {
        insertTransaction();

        SQLException failure = assertThrows(SQLException.class, connection::commit,
                "a transaction without postings must be rejected at commit");

        assertTrue(failure.getMessage().contains("has no postings"),
                "the rejection must name the missing postings, was: " + failure.getMessage());
    }

    @Test
    void updateOnPostingIsRejected() throws SQLException {
        insertBalancedTransactionAndCommit();

        SQLException failure = assertThrows(SQLException.class, () -> {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE posting SET amount = amount + 1");
            }
        }, "UPDATE on posting must be rejected");

        assertTrue(failure.getMessage().contains("append-only"),
                "the rejection must say the journal is append-only, was: " + failure.getMessage());
    }

    @Test
    void deleteOnPostingIsRejected() throws SQLException {
        insertBalancedTransactionAndCommit();

        SQLException failure = assertThrows(SQLException.class, () -> {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM posting");
            }
        }, "DELETE on posting must be rejected");

        assertTrue(failure.getMessage().contains("append-only"),
                "the rejection must say the journal is append-only, was: " + failure.getMessage());
    }

    @Test
    void secondDefaultCurrencyViolatesTheOneDefaultIndex() throws SQLException {
        insertCurrency("GEMS", true);

        SQLException failure = assertThrows(SQLException.class, () -> insertCurrency("SILVER", true),
                "a second default currency must violate the partial unique index");

        assertTrue(failure.getMessage().contains("currency_one_default"),
                "the violated index must be currency_one_default, was: " + failure.getMessage());
    }

    private void insertBalancedTransactionAndCommit() throws SQLException {
        UUID account = insertPlayerAccount();
        UUID other = insertPlayerAccount();
        UUID transaction = insertTransaction();
        insertPosting(transaction, account, "100000000");
        insertPosting(transaction, other, "-100000000");
        connection.commit();
    }

    private UUID insertPlayerAccount() throws SQLException {
        UUID id = UUID.randomUUID();
        try (var statement = connection.prepareStatement(
                "INSERT INTO account (id, kind, owner_uuid, created_at) VALUES (?, 'PLAYER', ?, ?)")) {
            statement.setObject(1, id);
            statement.setObject(2, UUID.randomUUID());
            statement.setTimestamp(3, Timestamp.from(NOW));
            statement.executeUpdate();
        }
        return id;
    }

    private UUID insertTransaction() throws SQLException {
        UUID id = UUID.randomUUID();
        try (var statement = connection.prepareStatement(
                "INSERT INTO transaction (id, kind, created_at) VALUES (?, 'TRANSFER', ?)")) {
            statement.setObject(1, id);
            statement.setTimestamp(2, Timestamp.from(NOW));
            statement.executeUpdate();
        }
        return id;
    }

    private void insertPosting(UUID transaction, UUID account, String scaledAmount) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO posting (id, transaction_id, account_id, currency, amount, created_at) "
                        + "VALUES (?, ?, ?, 'COINS', ?, ?)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, transaction);
            statement.setObject(3, account);
            statement.setBigDecimal(4, new java.math.BigDecimal(scaledAmount).movePointLeft(8));
            statement.setTimestamp(5, Timestamp.from(NOW));
            statement.executeUpdate();
        }
    }

    private void insertCurrency(String code, boolean defaultCurrency) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO currency (id, code, name_singular, name_plural, symbol, fraction_digits, is_default, "
                        + "created_at, updated_at) VALUES (?, ?, 'Coin', 'Coins', 'C', 2, ?, ?, ?)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, code);
            statement.setBoolean(3, defaultCurrency);
            statement.setTimestamp(4, Timestamp.from(NOW));
            statement.setTimestamp(5, Timestamp.from(NOW));
            statement.executeUpdate();
        }
        connection.commit();
    }
}
