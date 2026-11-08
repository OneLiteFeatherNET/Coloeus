package net.onelitefeather.coloeus.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemDetails;

/**
 * Helpers for HTTP tests: bearer requests, the answer of a refused request and its problem document.
 */
public final class Api {

    private Api() {
    }

    /** The request with a bearer token. */
    public static MutableHttpRequest<?> bearer(MutableHttpRequest<?> request, String token) {
        return request.bearerAuth(token);
    }

    /**
     * Sends the request and returns the refusal.
     *
     * @throws AssertionError when the service answers with success
     */
    public static HttpClientResponseException refusal(HttpClient client, HttpRequest<?> request) {
        try {
            client.toBlocking().exchange(request);
        } catch (HttpClientResponseException e) {
            return e;
        }
        throw new AssertionError("expected a refusal for " + request.getMethod() + " " + request.getPath());
    }

    /** Asserts status, {@code application/problem+json} and the stable code. */
    public static void assertProblem(HttpClientResponseException exception, HttpStatus status, ProblemCode code) {
        assertEquals(status, exception.getStatus(), "the HTTP status must match " + code);
        assertEquals(MediaType.of("application/problem+json"), exception.getResponse().getContentType().orElseThrow(),
                "errors must be answered as application/problem+json");
        ProblemDetails body = exception.getResponse().getBody(ProblemDetails.class).orElseThrow();
        assertEquals(code.name(), body.code(), "the problem code must be " + code);
    }
}
