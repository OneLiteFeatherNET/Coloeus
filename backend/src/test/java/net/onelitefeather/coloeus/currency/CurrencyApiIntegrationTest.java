package net.onelitefeather.coloeus.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import javax.money.Monetary;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemDetails;
import net.onelitefeather.coloeus.support.PostgresTestContainer;
import net.onelitefeather.coloeus.support.TestDatabase;
import net.onelitefeather.coloeus.support.TestTokens;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CurrencyApiIntegrationTest implements TestPropertyProvider {

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

    @BeforeEach
    void emptyRegistry() {
        TestDatabase.clearRegistry(POSTGRES);
    }

    @Test
    void createStoresTheCurrencyAndListsIt() {
        HttpResponse<Currency> created = create("COINS", 2);

        assertEquals(HttpStatus.CREATED, created.status(), "POST /v1/currencies should answer 201 Created");
        List<Currency> listed = client.toBlocking().retrieve(as(HttpRequest.GET("/v1/currencies"), TOKENS.admin()),
                Argument.listOf(Currency.class));
        assertEquals(1, listed.size(), "GET /v1/currencies should list the created currency");
        assertEquals(2, listed.get(0).fractionDigits(), "COINS must keep its two fraction digits");
    }

    @Test
    void firstCurrencyBecomesTheDefault() {
        Currency created = create("COINS", 2).body();

        assertTrue(created.defaultCurrency(), "the first currency must be the default one");
    }

    @Test
    void duplicateCodeAnswers409CurrencyExists() {
        create("COINS", 2);

        HttpClientResponseException problem = assertThrowsStatus(
                as(HttpRequest.POST("/v1/currencies", newCurrency("COINS", 2)), TOKENS.admin()));

        assertProblem(problem, HttpStatus.CONFLICT, ProblemCode.CURRENCY_EXISTS);
    }

    @Test
    void changingFractionDigitsAnswers409CurrencyScaleImmutable() {
        create("COINS", 2);

        HttpClientResponseException problem = assertThrowsStatus(as(
                HttpRequest.PUT("/v1/currencies/COINS", new UpdateCurrencyRequest("Coin", "Coins", "C", 3)),
                TOKENS.admin()));

        assertProblem(problem, HttpStatus.CONFLICT, ProblemCode.CURRENCY_SCALE_IMMUTABLE);
        assertEquals(2, currencyByCode("COINS").fractionDigits(),
                "after the refused update COINS must still have two fraction digits");
    }

    @Test
    void makingAnotherCurrencyDefaultUnmarksThePreviousOne() {
        create("COINS", 2);
        create("GEMS", 0);

        client.toBlocking().exchange(as(HttpRequest.PUT("/v1/currencies/GEMS/default", null), TOKENS.admin()));

        assertTrue(currencyByCode("GEMS").defaultCurrency(), "GEMS must be the default after the switch");
        assertFalse(currencyByCode("COINS").defaultCurrency(), "COINS must no longer be the default");
    }

    @Test
    void makingAnUnknownCurrencyDefaultAnswers404CurrencyNotFound() {
        HttpClientResponseException problem = assertThrowsStatus(
                as(HttpRequest.PUT("/v1/currencies/NOPE/default", null), TOKENS.admin()));

        assertProblem(problem, HttpStatus.NOT_FOUND, ProblemCode.CURRENCY_NOT_FOUND);
    }

    @Test
    void createdCurrencyResolvesThroughJsr354InThisService() {
        create("GEMS", 3);

        assertEquals(3, Monetary.getCurrency("GEMS").getDefaultFractionDigits(),
                "Monetary.getCurrency must resolve GEMS right after its creation");
    }

    @Test
    void clientCannotCreateACurrency() {
        HttpClientResponseException problem = assertThrowsStatus(
                as(HttpRequest.POST("/v1/currencies", newCurrency("COINS", 2)), TOKENS.client()));

        assertProblem(problem, HttpStatus.FORBIDDEN, ProblemCode.FORBIDDEN_ROLE);
    }

    @Test
    void moderatorCanReadButNotWriteCurrencies() {
        create("COINS", 2);

        List<Currency> listed = client.toBlocking().retrieve(
                as(HttpRequest.GET("/v1/currencies"), TOKENS.moderator()), Argument.listOf(Currency.class));
        HttpClientResponseException problem = assertThrowsStatus(
                as(HttpRequest.PUT("/v1/currencies/COINS/default", null), TOKENS.moderator()));

        assertEquals(1, listed.size(), "a moderator may list the currencies");
        assertProblem(problem, HttpStatus.FORBIDDEN, ProblemCode.FORBIDDEN_ROLE);
    }

    @Test
    void anonymousRequestCannotListCurrencies() {
        HttpClientResponseException problem = assertThrowsStatus(HttpRequest.GET("/v1/currencies"));

        assertProblem(problem, HttpStatus.UNAUTHORIZED, ProblemCode.UNAUTHORIZED);
    }

    private HttpResponse<Currency> create(String code, int fractionDigits) {
        return client.toBlocking().exchange(
                as(HttpRequest.POST("/v1/currencies", newCurrency(code, fractionDigits)), TOKENS.admin()),
                Currency.class);
    }

    private Currency currencyByCode(String code) {
        return client.toBlocking().retrieve(as(HttpRequest.GET("/v1/currencies"), TOKENS.admin()),
                        Argument.listOf(Currency.class))
                .stream()
                .filter(currency -> currency.code().equals(code))
                .findFirst()
                .orElseThrow();
    }

    private static MutableHttpRequest<?> as(MutableHttpRequest<?> request, String token) {
        return request.bearerAuth(token);
    }

    private static CreateCurrencyRequest newCurrency(String code, int fractionDigits) {
        return new CreateCurrencyRequest(code, "Coin", "Coins", "C", fractionDigits);
    }

    private HttpClientResponseException assertThrowsStatus(HttpRequest<?> request) {
        try {
            client.toBlocking().exchange(request);
        } catch (HttpClientResponseException e) {
            return e;
        }
        throw new AssertionError("expected an error answer for " + request.getMethod() + " " + request.getPath());
    }

    private static void assertProblem(HttpClientResponseException exception, HttpStatus status, ProblemCode code) {
        assertEquals(status, exception.getStatus(), "the HTTP status must match " + code);
        assertEquals(MediaType.of("application/problem+json"), exception.getResponse().getContentType().orElseThrow(),
                "errors must be answered as application/problem+json");
        ProblemDetails body = exception.getResponse().getBody(ProblemDetails.class).orElseThrow();
        assertEquals(code.name(), body.code(), "the problem code must be " + code);
    }
}
