package net.onelitefeather.coloeus.admin;

import io.micronaut.http.HttpMethod;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import net.onelitefeather.coloeus.auth.Roles;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;

/**
 * Who may call the staff API (staff-api spec, "Bearer authorization of the backend staff API"). A call is authorized
 * only by a delegated user token: its scope claim {@code scp} contains {@link #ACCESS_SCOPE}, its {@code oid} is present,
 * and its {@code idtyp}, where present, is {@code user}. A client-credential token has no scope claim and is refused.
 * Reads need {@link Roles#MODERATOR} or {@link Roles#ADMIN}; writes need {@link Roles#ADMIN}.
 */
final class StaffAccess {

    static final String ACCESS_SCOPE = "Coloeus.Access";

    static final String SCOPE_CLAIM = "scp";
    static final String OBJECT_ID_CLAIM = "oid";
    static final String NAME_CLAIM = "name";
    static final String TOKEN_TYPE_CLAIM = "idtyp";

    private StaffAccess() {
    }

    /**
     * The staff caller of a verified token.
     *
     * @param claims the claims of the token
     * @param roles  the app roles of the token
     * @throws ProblemException {@link ProblemCode#UNAUTHORIZED} when the token is not a delegated user token
     */
    static StaffCaller caller(Map<String, Object> claims, Collection<String> roles) {
        if (!hasAccessScope(claims.get(SCOPE_CLAIM))) {
            throw ProblemException.of(ProblemCode.UNAUTHORIZED,
                    "the staff API takes a delegated user token with the scope " + ACCESS_SCOPE);
        }
        String oid = text(claims.get(OBJECT_ID_CLAIM));
        if (oid == null || oid.isBlank()) {
            throw ProblemException.of(ProblemCode.UNAUTHORIZED, "the token carries no oid claim");
        }
        Object tokenType = claims.get(TOKEN_TYPE_CLAIM);
        if (tokenType != null && !"user".equals(String.valueOf(tokenType))) {
            throw ProblemException.of(ProblemCode.UNAUTHORIZED, "the staff API takes user tokens only");
        }
        return new StaffCaller(new StaffActor(oid, text(claims.get(NAME_CLAIM))),
                roles == null ? Set.of() : Set.copyOf(roles));
    }

    /**
     * Checks the role of the caller for the method of the request.
     *
     * @throws ProblemException {@link ProblemCode#FORBIDDEN_ROLE} when the role does not allow the method
     */
    static void require(StaffCaller caller, HttpMethod method) {
        if (isWrite(method)) {
            if (!caller.roles().contains(Roles.ADMIN)) {
                throw ProblemException.of(ProblemCode.FORBIDDEN_ROLE, "this write needs the " + Roles.ADMIN + " role");
            }
            return;
        }
        if (!caller.roles().contains(Roles.ADMIN) && !caller.roles().contains(Roles.MODERATOR)) {
            throw ProblemException.of(ProblemCode.FORBIDDEN_ROLE,
                    "this read needs the " + Roles.MODERATOR + " or " + Roles.ADMIN + " role");
        }
    }

    /** POST, PUT, PATCH and DELETE change state; GET and HEAD do not. */
    static boolean isWrite(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH
                || method == HttpMethod.DELETE;
    }

    /** The scope claim is a space separated string in Entra's access tokens; a list is accepted as well. */
    static boolean hasAccessScope(Object scope) {
        if (scope instanceof Collection<?> scopes) {
            return scopes.contains(ACCESS_SCOPE);
        }
        return scope != null && Arrays.asList(String.valueOf(scope).trim().split("\\s+")).contains(ACCESS_SCOPE);
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
