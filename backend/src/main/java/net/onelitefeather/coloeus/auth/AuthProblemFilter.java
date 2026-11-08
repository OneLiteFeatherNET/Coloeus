package net.onelitefeather.coloeus.auth;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemDetails;
import net.onelitefeather.coloeus.problem.ProblemException;
import net.onelitefeather.coloeus.problem.ProblemExceptionHandler;
import org.reactivestreams.Publisher;

/**
 * Answers the security refusals of {@code /v1} (401 without a valid token, 403 without the role) as RFC 9457 problem
 * documents. Micronaut Security writes those answers itself, outside of any exception handler, so this filter runs
 * before the security filter and rewrites their body. Problem answers of the service pass through unchanged.
 */
@Filter("/v1/**")
public class AuthProblemFilter implements HttpServerFilter {

    static final String WWW_AUTHENTICATE = HttpHeaders.WWW_AUTHENTICATE;

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        return Publishers.map(chain.proceed(request), AuthProblemFilter::asProblem);
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.SECURITY.order() - 1;
    }

    static MutableHttpResponse<?> asProblem(MutableHttpResponse<?> response) {
        HttpStatus status = response.getStatus();
        if (status != HttpStatus.UNAUTHORIZED && status != HttpStatus.FORBIDDEN) {
            return response;
        }
        if (response.getContentType().filter(ProblemExceptionHandler.PROBLEM_JSON::equals).isPresent()) {
            return response;
        }
        ProblemCode code = status == HttpStatus.UNAUTHORIZED ? ProblemCode.UNAUTHORIZED : ProblemCode.FORBIDDEN_ROLE;
        ProblemException problem = ProblemException.of(code, detail(code));
        MutableHttpResponse<ProblemDetails> answer = HttpResponse.<ProblemDetails>status(status)
                .contentType(ProblemExceptionHandler.PROBLEM_JSON)
                .body(ProblemDetails.of(problem));
        response.getHeaders().getFirst(WWW_AUTHENTICATE)
                .ifPresent(challenge -> answer.header(WWW_AUTHENTICATE, challenge));
        return answer;
    }

    private static String detail(ProblemCode code) {
        return code == ProblemCode.UNAUTHORIZED
                ? "send a valid Entra ID access token for the Coloeus audience as a bearer token"
                : "the token lacks an app role this route requires";
    }
}
