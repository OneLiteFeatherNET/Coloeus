package net.onelitefeather.coloeus.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemDetails;

/**
 * Asserts that an error answer is an RFC 9457 problem document with the expected status and code.
 */
public final class ProblemAssertions {

    private ProblemAssertions() {
    }

    public static void assertProblem(HttpClientResponseException exception, HttpStatus status, ProblemCode code) {
        assertEquals(status, exception.getStatus(), "the HTTP status must match " + code);
        assertEquals(MediaType.of("application/problem+json"), exception.getResponse().getContentType().orElseThrow(),
                "errors must be answered as application/problem+json");
        ProblemDetails body = exception.getResponse().getBody(ProblemDetails.class).orElseThrow();
        assertEquals(code.name(), body.code(), "the problem code must be " + code);
    }
}
