package net.onelitefeather.coloeus.request;

import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.ConversionErrorHandler;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.exceptions.JsonExceptionHandler;
import io.micronaut.http.server.exceptions.UnsatisfiedRouteHandler;
import io.micronaut.json.JsonSyntaxException;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;
import jakarta.inject.Singleton;
import net.onelitefeather.coloeus.problem.ProblemDetails;
import net.onelitefeather.coloeus.problem.ProblemExceptionHandler;

/**
 * The framework rejects requests it cannot bind (malformed JSON, a missing body, a value of the wrong type) with its
 * own error bodies. These handlers replace the framework's handlers and answer the same requests as problem details
 * with REQUEST_INVALID, so clients handle one error format.
 */
public final class MalformedRequestHandlers {

    private MalformedRequestHandlers() {
    }

    /** Shared answer: the same problem document the service writes for its own rejections. */
    abstract static class Base<E extends Throwable> implements ExceptionHandler<E, HttpResponse<ProblemDetails>> {

        private final ProblemExceptionHandler problems;

        Base(ProblemExceptionHandler problems) {
            this.problems = problems;
        }

        @Override
        public HttpResponse<ProblemDetails> handle(HttpRequest request, E exception) {
            return problems.handle(request, RequestValues.invalid("the request could not be read as the expected "
                    + "input; check the body, the path and the query values"));
        }
    }

    /** A required body, header, path or query value is missing or cannot be bound. */
    @Singleton
    @Replaces(UnsatisfiedRouteHandler.class)
    public static final class UnboundArgument extends Base<UnsatisfiedRouteException> {
        public UnboundArgument(ProblemExceptionHandler problems) {
            super(problems);
        }
    }

    /** The body is not JSON. */
    @Singleton
    @Replaces(JsonExceptionHandler.class)
    public static final class JsonSyntax extends Base<JsonSyntaxException> {
        public JsonSyntax(ProblemExceptionHandler problems) {
            super(problems);
        }
    }

    /** A value cannot be converted to the declared type, such as a path UUID or a number. */
    @Singleton
    @Replaces(ConversionErrorHandler.class)
    public static final class Conversion extends Base<ConversionErrorException> {
        public Conversion(ProblemExceptionHandler problems) {
            super(problems);
        }
    }
}
