package net.onelitefeather.coloeus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
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

@Tag("integration")
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApplicationHealthTest implements TestPropertyProvider {

    // Started when the class loads, before the Micronaut context reads the datasource properties below.
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
    void healthEndpointAnswersOk() {
        var response = client.toBlocking().exchange(HttpRequest.GET("/health"));

        assertEquals(HttpStatus.OK, response.status(), "GET /health should answer 200 OK without a token");
    }

    @Test
    void livenessEndpointAnswersOk() {
        var response = client.toBlocking().exchange(HttpRequest.GET("/health/liveness"));

        assertEquals(HttpStatus.OK, response.status(), "GET /health/liveness should answer 200 OK without a token");
    }

    @Test
    void readinessEndpointAnswersOkWhileTheDatabaseIsUp() {
        var response = client.toBlocking().exchange(HttpRequest.GET("/health/readiness"));

        assertEquals(HttpStatus.OK, response.status(), "GET /health/readiness should answer 200 OK with the database up");
    }
}
