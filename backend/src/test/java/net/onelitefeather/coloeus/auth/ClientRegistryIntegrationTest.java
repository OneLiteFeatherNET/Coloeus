package net.onelitefeather.coloeus.auth;

import static net.onelitefeather.coloeus.support.Api.assertProblem;
import static net.onelitefeather.coloeus.support.Api.bearer;
import static net.onelitefeather.coloeus.support.Api.refusal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.core.type.Argument;
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
 * The client registry under {@code /v1/clients}: admin only, with grants replaced as a whole.
 */
@Tag("integration")
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientRegistryIntegrationTest implements TestPropertyProvider {

    private static final PostgreSQLContainer POSTGRES = PostgresTestContainer.start();
    private static final TestTokens TOKENS = TestTokens.start();
    private static final UUID CLIENT = UUID.fromString("11111111-1111-4111-8111-111111111111");

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
    }

    @Test
    void adminRegistersAClientWithoutGrants() {
        var response = client.toBlocking().exchange(
                admin(HttpRequest.POST("/v1/clients", new RegisterClientRequest(CLIENT.toString(), "Velocity"))),
                String.class);

        assertEquals(HttpStatus.CREATED, response.status(), "POST /v1/clients should answer 201 Created");
        assertTrue(response.body().contains("\"grants\":[]"), "a new client holds no grants: " + response.body());
    }

    @Test
    void registeringTheSameClientTwiceAnswers409ClientExists() {
        register(CLIENT);

        var failure = refusal(client, admin(
                HttpRequest.POST("/v1/clients", new RegisterClientRequest(CLIENT.toString(), "Velocity"))));

        assertProblem(failure, HttpStatus.CONFLICT, ProblemCode.CLIENT_EXISTS);
    }

    @Test
    void registeringWithANonUuidClientIdAnswers400RequestInvalid() {
        var failure = refusal(client, admin(
                HttpRequest.POST("/v1/clients", new RegisterClientRequest("not-a-uuid", "Velocity"))));

        assertProblem(failure, HttpStatus.BAD_REQUEST, ProblemCode.REQUEST_INVALID);
    }

    @Test
    void clientCannotRegisterClients() {
        var failure = refusal(client, bearer(
                HttpRequest.POST("/v1/clients", new RegisterClientRequest(CLIENT.toString(), "Velocity")),
                TOKENS.client()));

        assertProblem(failure, HttpStatus.FORBIDDEN, ProblemCode.FORBIDDEN_ROLE);
    }

    @Test
    void adminListsRegisteredClientsWithTheirGrants() {
        register(CLIENT);
        createCurrency("COINS");
        replaceGrants(CLIENT, new GrantRequest("COINS", "MINT"));

        List<ClientView> listed = client.toBlocking().retrieve(admin(HttpRequest.GET("/v1/clients")),
                Argument.listOf(ClientView.class));

        assertEquals(1, listed.size(), "GET /v1/clients should list the registered client");
        assertEquals(List.of(new GrantView("COINS", Permission.MINT)), listed.get(0).grants(),
                "the listed client must carry its grants");
    }

    @Test
    void replacingGrantsStoresEachDistinctGrantOnce() {
        register(CLIENT);
        createCurrency("COINS");

        List<GrantView> stored = replaceGrants(CLIENT,
                new GrantRequest("COINS", "MINT"), new GrantRequest("COINS", "READ"),
                new GrantRequest("COINS", "MINT"));

        assertEquals(2, stored.size(), "a grant that appears twice in the request is stored once");
    }

    @Test
    void replacingGrantsDropsTheOnesNotInTheRequest() {
        register(CLIENT);
        createCurrency("COINS");
        replaceGrants(CLIENT, new GrantRequest("COINS", "MINT"));

        List<GrantView> stored = replaceGrants(CLIENT, new GrantRequest("COINS", "BURN"));

        assertEquals(List.of(new GrantView("COINS", Permission.BURN)), stored,
                "PUT replaces all grants of the client, it does not add to them");
    }

    @Test
    void grantsForAnUnknownClientAnswer404ClientNotFound() {
        createCurrency("COINS");

        var failure = refusal(client, admin(HttpRequest.PUT("/v1/clients/" + CLIENT + "/grants",
                List.of(new GrantRequest("COINS", "MINT")))));

        assertProblem(failure, HttpStatus.NOT_FOUND, ProblemCode.CLIENT_NOT_FOUND);
    }

    @Test
    void grantsForAnUnknownCurrencyAnswer404CurrencyNotFound() {
        register(CLIENT);

        var failure = refusal(client, admin(HttpRequest.PUT("/v1/clients/" + CLIENT + "/grants",
                List.of(new GrantRequest("NOPE", "MINT")))));

        assertProblem(failure, HttpStatus.NOT_FOUND, ProblemCode.CURRENCY_NOT_FOUND);
    }

    @Test
    void anUnknownPermissionAnswers400RequestInvalid() {
        register(CLIENT);
        createCurrency("COINS");

        var failure = refusal(client, admin(HttpRequest.PUT("/v1/clients/" + CLIENT + "/grants",
                List.of(new GrantRequest("COINS", "SUPERUSER")))));

        assertProblem(failure, HttpStatus.BAD_REQUEST, ProblemCode.REQUEST_INVALID);
    }

    @Test
    void clientCannotReplaceItsOwnGrants() {
        register(CLIENT);
        createCurrency("COINS");

        var failure = refusal(client, bearer(
                HttpRequest.PUT("/v1/clients/" + CLIENT + "/grants", List.of(new GrantRequest("COINS", "MINT"))),
                TOKENS.client()));

        assertProblem(failure, HttpStatus.FORBIDDEN, ProblemCode.FORBIDDEN_ROLE);
    }

    private void register(UUID clientId) {
        client.toBlocking().exchange(
                admin(HttpRequest.POST("/v1/clients", new RegisterClientRequest(clientId.toString(), "Velocity"))));
    }

    private void createCurrency(String code) {
        client.toBlocking().exchange(admin(HttpRequest.POST("/v1/currencies",
                new CreateCurrencyRequest(code, "Coin", "Coins", "C", 2))));
    }

    private List<GrantView> replaceGrants(UUID clientId, GrantRequest... grants) {
        return client.toBlocking().retrieve(
                admin(HttpRequest.PUT("/v1/clients/" + clientId + "/grants", List.of(grants))),
                Argument.listOf(GrantView.class));
    }

    private HttpRequest<?> admin(MutableHttpRequest<?> request) {
        return bearer(request, TOKENS.admin());
    }
}
