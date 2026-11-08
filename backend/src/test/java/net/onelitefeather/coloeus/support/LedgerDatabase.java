package net.onelitefeather.coloeus.support;

import java.sql.Connection;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Prepares the ledger database of a test: the schema from the Flyway migrations, and empty ledger tables.
 */
public final class LedgerDatabase {

    private LedgerDatabase() {
    }

    /** Applies the migrations; a no-op when the schema is current. */
    public static void migrate(PostgreSQLContainer postgres) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
    }

    public static void truncate(PostgreSQLContainer postgres) throws SQLException {
        try (Connection connection = postgres.createConnection("");
             var statement = connection.createStatement()) {
            statement.execute("TRUNCATE posting, transaction, balance, idempotency_key, account, "
                    + "client_grant, client, player_name, currency, staff_action");
        }
    }
}
