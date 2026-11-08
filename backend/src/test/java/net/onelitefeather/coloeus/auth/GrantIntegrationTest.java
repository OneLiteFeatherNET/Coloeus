package net.onelitefeather.coloeus.auth;

import static net.onelitefeather.coloeus.support.Api.assertProblem;
import static net.onelitefeather.coloeus.support.Api.bearer;
import static net.onelitefeather.coloeus.support.Api.refusal;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.onelitefeather.coloeus.currency.CreateCurrencyRequest;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.support.PostgresTestContainer;
import net.onelitefeather.coloeus.support.TestDatabase;
import net.onelitefeather.coloeus.support.TestTokens;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The grant checks as a transaction endpoint applies them, exercised through the test-only probe route.
 */
@Tag("integration")
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GrantIntegrationTest implements TestPropertyProvider {

    private static final PostgreSQLContainer POSTGRES = PostgresTestContainer.start();
    private static final TestTokens TOKENS = TestTokens.start();
    private static final UUID VELOCITY = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID OWNER = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID OTHER_PLAYER = UUID.fromString("44444444-4444-4444-8444-444444444444");

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

    @BeforeEach
    void emptyRegistry() {
        TestDatabase.clearRegistry(POSTGRES);
        client.toBlocking().exchange(admin(HttpRequest.POST("/v1/currencies",
                new CreateCurrencyRequest("COINS", "Coin", "Coins", "C", 2))));
    }

    @Test
    void anUnregisteredClientCannotMint() {
        var failure = refusal(client, grant(TOKENS.clientOf(VELOCITY), "COINS", "MINT"));

        assertProblem(failure, HttpStatus.FORBIDDEN, ProblemCode.CLIENT_NOT_REGISTERED);
    }

    @Test
    void anUnregisteredClientMayRead() {
        var response = client.toBlocking().exchange(grant(TOKENS.clientOf(VELOCITY), "COINS", "READ"));

        assertEquals(HttpStatus.NO_CONTENT, response.status(), "an unregistered client may read");
    }

    @Test
    void aRegisteredClientWithoutTheGrantAnswers403ForbiddenGrant() {
        register(VELOCITY);

        var failure = refusal(client, grant(TOKENS.clientOf(VELOCITY), "COINS", "MINT"));

        assertProblem(failure, HttpStatus.FORBIDDEN, ProblemCode.FORBIDDEN_GRANT);
    }

    @Test
    void aRegisteredClientWithTheGrantMayMint() {
        register(VELOCITY);
        grantTo(VELOCITY, "COINS", "MINT");

        var response = client.toBlocking().exchange(grant(TOKENS.clientOf(VELOCITY), "COINS", "MINT"));

        assertEquals(HttpStatus.NO_CONTENT, response.status(), "the MINT grant on COINS allows minting COINS");
    }

    @Test
    void aGrantOnOneCurrencyDoesNotCoverAnother() {
        register(VELOCITY);
        grantTo(VELOCITY, "COINS", "BURN");

        var failure = refusal(client, grant(TOKENS.clientOf(VELOCITY), "GEMS", "BURN"));

        assertProblem(failure, HttpStatus.FORBIDDEN, ProblemCode.FORBIDDEN_GRANT);
    }

    @Test
    void aRegisteredClientMayActForThePlayerItNames() {
        register(VELOCITY);
        grantTo(VELOCITY, "COINS", "TRANSFER_ON_BEHALF");

        var response = client.toBlocking().exchange(onBehalf(TOKENS.clientOf(VELOCITY), OWNER, OWNER.toString()));

        assertEquals(HttpStatus.NO_CONTENT, response.status(), "the actor is the debited player");
    }

    @Test
    void aClientActingForAnotherPlayerAnswers403ActorMismatch() {
        register(VELOCITY);
        grantTo(VELOCITY, "COINS", "TRANSFER_ON_BEHALF");

        var failure = refusal(client, onBehalf(TOKENS.clientOf(VELOCITY), OWNER, OTHER_PLAYER.toString()));

        assertProblem(failure, HttpStatus.FORBIDDEN, ProblemCode.ACTOR_MISMATCH);
    }

    @Test
    void aClientWithoutTheTransferGrantCannotActForAPlayer() {
        register(VELOCITY);

        var failure = refusal(client, onBehalf(TOKENS.clientOf(VELOCITY), OWNER, OWNER.toString()));

        assertProblem(failure, HttpStatus.FORBIDDEN, ProblemCode.FORBIDDEN_GRANT);
    }

    @Test
    void anAdminMayActForAnyPlayerWithoutTheGrant() {
        var response = client.toBlocking().exchange(onBehalf(TOKENS.admin(), OWNER, OTHER_PLAYER.toString()));

        assertEquals(HttpStatus.NO_CONTENT, response.status(), "an admin may act for any player");
    }

    @Test
    void aClientWithAnEmptyActorCannotActForAPlayer() {
        register(VELOCITY);
        grantTo(VELOCITY, "COINS", "TRANSFER_ON_BEHALF");

        var failure = refusal(client, onBehalf(TOKENS.clientOf(VELOCITY), OWNER, null));

        assertProblem(failure, HttpStatus.FORBIDDEN, ProblemCode.ACTOR_MISMATCH);
    }

    private void register(UUID clientId) {
        client.toBlocking().exchange(admin(HttpRequest.POST("/v1/clients",
                new RegisterClientRequest(clientId.toString(), "Velocity"))));
    }

    private void grantTo(UUID clientId, String currency, String permission) {
        client.toBlocking().exchange(admin(HttpRequest.PUT("/v1/clients/" + clientId + "/grants",
                List.of(new GrantRequest(currency, permission)))));
    }

    private static HttpRequest<?> grant(String token, String currency, String permission) {
        return bearer(HttpRequest.POST("/v1/probe/grants/" + currency + "/" + permission, null), token);
    }

    private static HttpRequest<?> onBehalf(String token, UUID owner, String actor) {
        MutableHttpRequest<?> request = HttpRequest.POST("/v1/probe/on-behalf/" + owner, null);
        if (actor != null) {
            request = request.uri(uri -> uri.queryParam("actor", actor));
        }
        return bearer(request, token);
    }

    private HttpRequest<?> admin(MutableHttpRequest<?> request) {
        return bearer(request, TOKENS.admin());
    }
}
