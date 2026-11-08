package net.onelitefeather.coloeus.support;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import net.onelitefeather.coloeus.auth.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class of the ledger integration tests: one Postgres container and one identity provider for the class, and a
 * fresh ledger and registry for every test. Requests carry signed tokens ({@link TestTokens}); a test registers the
 * clients and grants it needs with {@link #registerClient} and {@link #grant}. Subclasses add
 * {@code @MicronautTest(transactional = false)} and {@code @TestInstance(PER_CLASS)}.
 */
@Tag("integration")
public abstract class LedgerApiIntegrationTest implements TestPropertyProvider {

    // Started when the class loads, before the Micronaut context reads the datasource properties below.
    protected static final PostgreSQLContainer POSTGRES = PostgresTestContainer.start();

    // Signs and publishes the tokens of every test in this JVM. Stateless after start, so it is shared; the
    // identity provider stays up until the JVM ends, like the container.
    protected static final TestTokens TOKENS = TestTokens.start();

    @Inject
    @Client("/")
    protected HttpClient client;

    @Inject
    protected MutableClock clock;

    @Override
    public Map<String, String> getProperties() {
        return TestDatabase.properties(POSTGRES, TOKENS);
    }

    @BeforeEach
    void emptyLedger() throws SQLException {
        LedgerDatabase.truncate(POSTGRES);
    }

    /** A GET with the given bearer token. */
    protected static MutableHttpRequest<?> get(String token, String path) {
        return HttpRequest.GET(path).bearerAuth(token);
    }

    /** Registers a client by its Entra application id, without grants. */
    protected static void registerClient(UUID app) {
        execute("INSERT INTO client (client_id, display_name, created_at) VALUES (?, ?, now())",
                app, "test client " + app);
    }

    /** Grants a permission on a currency to a registered client. */
    protected static void grant(UUID app, String currency, Permission permission) {
        execute("INSERT INTO client_grant (client_id, currency, permission) VALUES (?, ?, ?)",
                app, currency, permission.name());
    }

    /** A direct JDBC connection to the test database, for assertions on stored rows. */
    protected static Connection connection() throws SQLException {
        return POSTGRES.createConnection("");
    }

    /** Runs the work on a connection and closes it. */
    protected static <T> T query(SqlQuery<T> query) {
        try (Connection connection = connection()) {
            return query.run(connection);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void execute(String sql, Object... values) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("fixture statement failed: " + sql, e);
        }
    }

    @FunctionalInterface
    protected interface SqlQuery<T> {
        T run(Connection connection) throws SQLException;
    }
}
