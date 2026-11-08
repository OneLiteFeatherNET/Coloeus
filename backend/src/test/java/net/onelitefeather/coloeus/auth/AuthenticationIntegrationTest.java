package net.onelitefeather.coloeus.auth;

import static net.onelitefeather.coloeus.support.Api.assertProblem;
import static net.onelitefeather.coloeus.support.Api.bearer;
import static net.onelitefeather.coloeus.support.Api.refusal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.util.Map;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.support.PostgresTestContainer;
import net.onelitefeather.coloeus.support.TestDatabase;
import net.onelitefeather.coloeus.support.TestTokens;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Bearer token rules of the API, end to end: who is refused and how, and which routes stay anonymous.
 */
@Tag("integration")
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthenticationIntegrationTest implements TestPropertyProvider {

    private static final PostgreSQLContainer POSTGRES = PostgresTestContainer.start();
    private static final TestTokens TOKENS = TestTokens.start();
    private static final String OTHER_TENANT_ISSUER = "https://login.microsoftonline.com/"
            + "00000000-0000-4000-8000-0000000000bb/v2.0";

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
    void aRequestWithoutATokenAnswers401AsProblem() {
        var failure = refusal(client, HttpRequest.GET("/v1/currencies"));

        assertProblem(failure, HttpStatus.UNAUTHORIZED, ProblemCode.UNAUTHORIZED);
    }

    @Test
    void anExpiredTokenAnswers401AsProblem() {
        String token = TOKENS.token().roles(TestTokens.Roles.CLIENT).expired().sign();

        var failure = refusal(client, bearer(HttpRequest.GET("/v1/currencies"), token));

        assertProblem(failure, HttpStatus.UNAUTHORIZED, ProblemCode.UNAUTHORIZED);
    }

    @Test
    void aTokenFromAnotherTenantAnswers401() {
        String token = TOKENS.token().roles(TestTokens.Roles.CLIENT).issuer(OTHER_TENANT_ISSUER).sign();

        var failure = refusal(client, bearer(HttpRequest.GET("/v1/currencies"), token));

        assertProblem(failure, HttpStatus.UNAUTHORIZED, ProblemCode.UNAUTHORIZED);
    }

    @Test
    void aTokenForAnotherAudienceAnswers401() {
        String token = TOKENS.token().roles(TestTokens.Roles.CLIENT).audience("some-other-api").sign();

        var failure = refusal(client, bearer(HttpRequest.GET("/v1/currencies"), token));

        assertProblem(failure, HttpStatus.UNAUTHORIZED, ProblemCode.UNAUTHORIZED);
    }

    @Test
    void aTokenSignedByAnUntrustedKeyAnswers401() {
        String token = TOKENS.token().roles(TestTokens.Roles.CLIENT).signedByAnotherKey().sign();

        var failure = refusal(client, bearer(HttpRequest.GET("/v1/currencies"), token));

        assertProblem(failure, HttpStatus.UNAUTHORIZED, ProblemCode.UNAUTHORIZED);
    }

    @Test
    void aValidClientTokenReachesTheRoute() {
        var response = client.toBlocking().exchange(bearer(HttpRequest.GET("/v1/currencies"), TOKENS.client()));

        assertEquals(HttpStatus.OK, response.status(), "a valid Coloeus.Client token may read currencies");
    }

    @Test
    void aClientTokenWithoutAnyColoeusRoleAnswers403FromTheRoleCheck() {
        String token = TOKENS.token().roles("Other.Role").sign();

        var failure = refusal(client, bearer(HttpRequest.POST("/v1/currencies", "{}"), token));

        assertProblem(failure, HttpStatus.FORBIDDEN, ProblemCode.FORBIDDEN_ROLE);
    }

    @Test
    void aRouteWithoutAnySecurityRuleIsRefusedEvenForAValidToken() {
        var failure = refusal(client, bearer(HttpRequest.GET("/v1/probe/unannotated"), TOKENS.admin()));

        assertTrue(failure.getStatus() == HttpStatus.UNAUTHORIZED || failure.getStatus() == HttpStatus.FORBIDDEN,
                "a route without a @Secured rule must be refused, but answered " + failure.getStatus());
    }

    @Test
    void healthAnswersWithoutAToken() {
        var response = client.toBlocking().exchange(HttpRequest.GET("/health"));

        assertEquals(HttpStatus.OK, response.status(), "the health endpoint stays anonymous");
    }

    @Test
    void theOpenApiDocumentIsServedAnonymously() {
        String document = client.toBlocking().retrieve(HttpRequest.GET("/swagger/coloeus.yml"));

        assertTrue(document.contains("title: Coloeus"), "the OpenAPI document must be titled Coloeus");
    }
}
