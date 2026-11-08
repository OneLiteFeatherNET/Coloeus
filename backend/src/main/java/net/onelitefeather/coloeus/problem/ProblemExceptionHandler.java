package net.onelitefeather.coloeus.problem;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

/**
 * Answers every {@link ProblemException} as {@code application/problem+json}.
 */
@Singleton
public class ProblemExceptionHandler implements ExceptionHandler<ProblemException, HttpResponse<ProblemDetails>> {

    public static final MediaType PROBLEM_JSON = MediaType.of("application/problem+json");

    @Override
    public HttpResponse<ProblemDetails> handle(HttpRequest request, ProblemException exception) {
        return HttpResponse.<ProblemDetails>status(exception.status())
                .contentType(PROBLEM_JSON)
                .body(ProblemDetails.of(exception));
    }
}
