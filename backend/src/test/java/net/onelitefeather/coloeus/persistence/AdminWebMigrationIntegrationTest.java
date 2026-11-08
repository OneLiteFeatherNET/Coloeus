package net.onelitefeather.coloeus.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.postgresql.PostgreSQLContainer;
import net.onelitefeather.coloeus.support.LedgerDatabase;
import net.onelitefeather.coloeus.support.PostgresTestContainer;

/**
 * V2 of the schema on a database that already holds V1 data: the idempotency keys get their client scope and keep their
 * answers, and the new tables and check values exist. Every test starts from an empty database that holds V1 data.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminWebMigrationIntegrationTest {

    static final String CLIENT = "00000000-0000-4000-8000-0000000000c1";
    static final String ANSWER = "{\"id\": \"5f0c0000-0000-4000-8000-000000000001\", \"kind\": \"MINT\"}";

    private final PostgreSQLContainer postgres = PostgresTestContainer.start();

    @BeforeEach
    void schemaAtVersionOneWithOneKey() throws SQLException {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target(MigrationVersion.fromVersion("1"))
                .load()
                .migrate();
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO client (client_id, display_name, created_at) "
                    + "VALUES ('" + CLIENT + "', 'paper-lobby-1', now())");
            statement.execute("INSERT INTO idempotency_key (client_id, key, request_hash, response, expires_at) "
                    + "VALUES ('" + CLIENT + "', 'key-1', 'hash-1', '" + ANSWER + "'::jsonb, now() + interval '1 day')");
        }
        LedgerDatabase.migrate(postgres);
    }

    @Test
    void existingKeyBecomesClientScoped() throws SQLException {
        assertEquals("client:" + CLIENT, query("SELECT scope FROM idempotency_key WHERE key = 'key-1'"));
    }

    @Test
    void existingKeyKeepsItsStoredAnswer() throws SQLException {
        String stored = query("SELECT response::text FROM idempotency_key WHERE key = 'key-1'");

        assertTrue(stored.contains("\"kind\""), "the stored answer must survive the migration");
        assertEquals(ANSWER.replace(" ", ""), stored.replace(" ", ""));
    }

    @Test
    void existingKeyKeepsItsRequestHash() throws SQLException {
        assertEquals("hash-1", query("SELECT request_hash FROM idempotency_key WHERE key = 'key-1'"));
    }

    @Test
    void staffKeyIsStoredWithoutClient() throws SQLException {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO idempotency_key (scope, client_id, key, request_hash, expires_at) "
                    + "VALUES ('staff:oid-1', NULL, 'key-2', 'hash-2', now() + interval '1 day')");
        }

        assertEquals("1", query("SELECT count(*) FROM idempotency_key WHERE scope = 'staff:oid-1'"));
    }

    @Test
    void clientScopeWithoutClientIsRefused() throws SQLException {
        assertThrows(SQLException.class, () -> {
            try (Connection connection = postgres.createConnection("");
                 Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO idempotency_key (scope, client_id, key, request_hash, expires_at) "
                        + "VALUES ('client:" + CLIENT + "', NULL, 'key-3', 'hash-3', now() + interval '1 day')");
            }
        }, "a client scope needs its client");
    }

    @Test
    void transactionKindCheckAllowsTheStaffKinds() throws SQLException {
        String definition = query("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                + "WHERE conname = 'transaction_kind_check'");

        assertTrue(definition.contains("'RESET'") && definition.contains("'ADJUST'"),
                "the kind check must name RESET and ADJUST, was: " + definition);
    }

    @Test
    void postingsHaveAnIndexOnCreationTime() throws SQLException {
        assertEquals("1", query("SELECT count(*) FROM pg_indexes WHERE tablename = 'posting' "
                + "AND indexname = 'posting_created_at'"));
    }

    @Test
    void staffActionRowsCannotBeUpdated() throws SQLException {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO staff_action (id, actor_oid, actor_name, action, target, created_at) "
                    + "VALUES ('5f0c0000-0000-4000-8000-0000000000a1', 'oid-1', 'Alex', 'CLIENT_REGISTERED', "
                    + "'paper-lobby-1', now())");
            SQLException refused = assertThrows(SQLException.class,
                    () -> statement.execute("UPDATE staff_action SET actor_name = 'Mallory'"));
            assertFalse(refused.getMessage().isBlank(), "the database must say why it refused the update");
        }
    }

    private String query(String sql) throws SQLException {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement();
             var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), "the query must return a row: " + sql);
            return rows.getString(1);
        }
    }
}
