package net.onelitefeather.coloeus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.util.Map;
import net.onelitefeather.coloeus.support.PostgresTestContainer;
import net.onelitefeather.coloeus.support.TestDatabase;
import net.onelitefeather.coloeus.support.TestTokens;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Readiness must fail while the database is unreachable, so the replica leaves the load balancer.
 */
@Tag("integration")
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReadinessDatabaseTest implements TestPropertyProvider {

    private static final PostgreSQLContainer POSTGRES = PostgresTestContainer.start();
    private static final TestTokens TOKENS = TestTokens.start();

    @Inject
    @Client("/")
    HttpClient client;

    @Override
    public Map<String, String> getProperties() {
        return TestDatabase.properties(POSTGRES, TOKENS);
    }

    @AfterAll
    static void stopIdentityProvider() {
        TOKENS.close();
    }

    @Test
    void readinessFailsWhenTheDatabaseIsStopped() {
        POSTGRES.stop();

        HttpClientResponseException failure = assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/health/readiness")),
                "GET /health/readiness must not answer 200 while the database is down");

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.getStatus(),
                "readiness must answer 503 while the database is down");
    }
}
