package net.onelitefeather.coloeus.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Settings and resets of the Postgres container that an integration test class owns.
 */
public final class TestDatabase {

    private TestDatabase() {
    }

    /**
     * The datasource settings of the container, merged with the identity settings of {@code tokens}. The HTTP server
     * takes a free port per context, so parallel contexts never collide.
     */
    public static Map<String, String> properties(PostgreSQLContainer postgres, TestTokens tokens) {
        Map<String, String> properties = new HashMap<>(tokens.properties());
        properties.put("micronaut.server.port", "-1");
        properties.put("datasources.default.url", postgres.getJdbcUrl());
        properties.put("datasources.default.username", postgres.getUsername());
        properties.put("datasources.default.password", postgres.getPassword());
        return properties;
    }

    /** Empties the tables of the registry, the names and the currencies, children first. */
    public static void clearRegistry(PostgreSQLContainer postgres) {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM client_grant");
            statement.execute("DELETE FROM client");
            statement.execute("DELETE FROM player_name");
            statement.execute("DELETE FROM currency");
        } catch (SQLException e) {
            throw new IllegalStateException("cannot reset the registry tables", e);
        }
    }
}
