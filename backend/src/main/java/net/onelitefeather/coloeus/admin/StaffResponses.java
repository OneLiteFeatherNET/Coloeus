package net.onelitefeather.coloeus.admin;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import net.onelitefeather.coloeus.problem.ProblemDetails;
import net.onelitefeather.coloeus.problem.ProblemException;
import org.reactivestreams.Publisher;

/**
 * Answers of the staff API: problem documents and the JSON of a stored answer.
 */
final class StaffResponses {

    static final MediaType PROBLEM_JSON = MediaType.of("application/problem+json");

    private StaffResponses() {
    }

    /** A problem document as a complete answer, for filters that refuse before the route runs. */
    static Publisher<MutableHttpResponse<?>> refuse(ProblemException problem) {
        MutableHttpResponse<?> answer = HttpResponse.status(problem.status())
                .contentType(PROBLEM_JSON)
                .body(ProblemDetails.of(problem));
        return Publishers.just(answer);
    }

    /** A problem document as a value, for controllers that return a response. */
    static MutableHttpResponse<ProblemDetails> problem(ProblemException problem) {
        return HttpResponse.<ProblemDetails>status(problem.status())
                .contentType(PROBLEM_JSON)
                .body(ProblemDetails.of(problem));
    }

    /** A JSON answer whose body is already serialized (the stored answer of an idempotent write). */
    static HttpResponse<String> json(HttpStatus status, String body) {
        return HttpResponse.<String>status(status)
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .body(body);
    }
}
