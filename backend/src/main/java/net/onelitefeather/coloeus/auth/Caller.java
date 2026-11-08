package net.onelitefeather.coloeus.auth;

import io.micronaut.security.authentication.Authentication;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Who calls the service: the client (the token's {@code azp}), the subject, the app roles and whether the caller is an
 * admin. Built from the current {@link Authentication} of a request.
 *
 * @param clientId the Entra client id of the calling application (or staff web app)
 * @param subject  the {@code sub} claim of the token
 * @param roles    the app roles of the token
 * @param admin    whether {@link Roles#ADMIN} is among the roles
 */
public record Caller(UUID clientId, String subject, Set<String> roles, boolean admin) {

    static final String AZP_CLAIM = "azp";
    static final String SUBJECT_CLAIM = "sub";

    public Caller {
        Objects.requireNonNull(clientId, "clientId");
        roles = Set.copyOf(roles);
    }

    /**
     * @throws ProblemException {@link ProblemCode#UNAUTHORIZED} when the token has no {@code azp} that is a UUID
     */
    public static Caller from(Authentication authentication) {
        Map<String, Object> claims = authentication.getAttributes();
        UUID clientId = parseClientId(claims.get(AZP_CLAIM));
        Set<String> roles = authentication.getRoles() == null ? Set.of() : Set.copyOf(authentication.getRoles());
        Object subject = claims.getOrDefault(SUBJECT_CLAIM, authentication.getName());
        return new Caller(clientId, String.valueOf(subject), roles, roles.contains(Roles.ADMIN));
    }

    private static UUID parseClientId(Object azp) {
        try {
            return UUID.fromString(String.valueOf(azp));
        } catch (IllegalArgumentException e) {
            throw ProblemException.of(ProblemCode.UNAUTHORIZED, "the token has no azp claim that is a client id");
        }
    }
}
