package net.onelitefeather.coloeus.admin;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.ErrorExceptionHandler;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The 500 answer of the staff API: an unexpected failure under {@code /v1/staff} becomes {@code INTERNAL} with a detail
 * that names no cause. The cause goes to the service log only. Every other path, including the rest of {@code /v1}, is
 * answered by the framework's own handler, as before.
 */
@Singleton
public class AdminErrors implements ExceptionHandler<Throwable, HttpResponse<?>> {

    static final String DETAIL = "the staff request could not be completed; the service log has the cause";

    private static final Logger LOG = LoggerFactory.getLogger(AdminErrors.class);

    private final ErrorExceptionHandler framework;

    public AdminErrors(ErrorExceptionHandler framework) {
        this.framework = framework;
    }

    /** The problem of an unexpected failure. */
    static ProblemException internal() {
        return ProblemException.of(ProblemCode.INTERNAL, DETAIL);
    }

    @Override
    public HttpResponse<?> handle(HttpRequest request, Throwable exception) {
        if (request.getPath().startsWith(StaffRequestFilter.PREFIX)) {
            LOG.error("staff request failed: {} {}", request.getMethod(), request.getPath(), exception);
            return StaffResponses.problem(internal());
        }
        return framework.handle(request, exception);
    }
}
