package net.onelitefeather.coloeus.admin;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.security.authentication.Authentication;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The gate of the staff API under {@code /v1/staff}, before any body is read: the token must be a delegated staff token
 * ({@link StaffAccess#caller}, 401 {@code UNAUTHORIZED}) and carry the role of the method (403 {@code FORBIDDEN_ROLE}).
 * The verified caller is put on the request as {@link #CALLER_ATTRIBUTE}. An unexpected failure answers 500
 * {@code INTERNAL} without detail.
 */
@Filter(StaffRequestFilter.PREFIX + "**")
public class StaffRequestFilter implements HttpServerFilter {

    /** The request attribute that holds the {@link StaffCaller} of an accepted call. */
    public static final String CALLER_ATTRIBUTE = "coloeus.staff.caller";

    static final String PREFIX = "/v1/staff/";

    private static final Logger LOG = LoggerFactory.getLogger(StaffRequestFilter.class);

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        try {
            StaffCaller caller = verified(request);
            StaffAccess.require(caller, request.getMethod());
            request.setAttribute(CALLER_ATTRIBUTE, caller);
            return chain.proceed(request);
        } catch (ProblemException refusal) {
            LOG.warn("refused {} {}: {}", request.getMethod(), request.getPath(), refusal.getMessage());
            return StaffResponses.refuse(refusal);
        } catch (RuntimeException failure) {
            LOG.error("staff request failed at the gate: {} {}", request.getMethod(), request.getPath(), failure);
            return StaffResponses.refuse(AdminErrors.internal());
        }
    }

    /** The caller of an accepted request, for the controllers. */
    static StaffCaller callerOf(HttpRequest<?> request) {
        return request.getAttribute(CALLER_ATTRIBUTE, StaffCaller.class)
                .orElseThrow(() -> ProblemException.of(ProblemCode.UNAUTHORIZED,
                        "send a valid Entra ID access token of the staff API as a bearer token"));
    }

    /** The actor of an accepted request: the oid and the name of its token. */
    static StaffActor actorOf(HttpRequest<?> request) {
        return callerOf(request).actor();
    }

    private static StaffCaller verified(HttpRequest<?> request) {
        Authentication authentication = request.getUserPrincipal(Authentication.class)
                .orElseThrow(() -> ProblemException.of(ProblemCode.UNAUTHORIZED,
                        "send a valid Entra ID access token of the staff API as a bearer token"));
        return StaffAccess.caller(authentication.getAttributes(), authentication.getRoles());
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.SECURITY.order() + 1;
    }
}
