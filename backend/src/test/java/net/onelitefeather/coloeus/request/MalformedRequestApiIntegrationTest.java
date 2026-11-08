package net.onelitefeather.coloeus.request;

import static net.onelitefeather.coloeus.support.ProblemAssertions.assertProblem;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import java.util.UUID;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.support.LedgerApiIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Requests the framework cannot bind are answered as problem details with REQUEST_INVALID, not as the default
 * error bodies of the framework.
 */
@Tag("integration")
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MalformedRequestApiIntegrationTest extends LedgerApiIntegrationTest {

    private static final String CURRENCY_PATH = "/v1/currencies";

    @Test
    void malformedJsonBodyIsAProblemDetail() {
        HttpClientResponseException problem = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(json(HttpRequest.POST(CURRENCY_PATH, "{\"code\":")) ),
                "a body that is not JSON must be refused");

        assertProblem(problem, HttpStatus.BAD_REQUEST, ProblemCode.REQUEST_INVALID);
    }

    @Test
    void missingBodyIsAProblemDetail() {
        HttpClientResponseException problem = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(json(HttpRequest.POST(CURRENCY_PATH, null))),
                "a POST without a body must be refused");

        assertProblem(problem, HttpStatus.BAD_REQUEST, ProblemCode.REQUEST_INVALID);
    }

    @Test
    void wrongTypeOfAFieldIsAProblemDetail() {
        String body = "{\"code\":\"COINS\",\"nameSingular\":\"Coin\",\"namePlural\":\"Coins\","
                + "\"symbol\":\"C\",\"fractionDigits\":\"two\"}";

        HttpClientResponseException problem = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(json(HttpRequest.POST(CURRENCY_PATH, body))),
                "a number given as text must be refused");

        assertProblem(problem, HttpStatus.BAD_REQUEST, ProblemCode.REQUEST_INVALID);
    }

    @Test
    void malformedPathValueIsAProblemDetail() {
        HttpClientResponseException problem = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(get(TOKENS.client(), "/v1/accounts/player/not-a-uuid")),
                "a player path segment that is not a UUID must be refused");

        assertProblem(problem, HttpStatus.BAD_REQUEST, ProblemCode.REQUEST_INVALID);
    }

    @Test
    void nonIntegerQueryValueIsAProblemDetail() {
        HttpClientResponseException problem = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(get(TOKENS.client(),
                        "/v1/accounts/" + UUID.randomUUID() + "/postings?limit=many")),
                "a limit that is not a number must be refused");

        assertProblem(problem, HttpStatus.BAD_REQUEST, ProblemCode.REQUEST_INVALID);
    }

    /** A JSON request of the admin, who may write currencies. */
    private static io.micronaut.http.MutableHttpRequest<?> json(io.micronaut.http.MutableHttpRequest<?> request) {
        return request.contentType(MediaType.APPLICATION_JSON_TYPE).bearerAuth(TOKENS.admin());
    }
}
