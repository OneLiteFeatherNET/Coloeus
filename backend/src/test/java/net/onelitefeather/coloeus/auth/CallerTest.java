package net.onelitefeather.coloeus.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.security.authentication.Authentication;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;
import org.junit.jupiter.api.Test;

class CallerTest {

    private static final UUID APP = UUID.fromString("00000000-0000-4000-8000-0000000000c1");

    @Test
    void theClientIdIsTheAzpClaim() {
        Caller caller = Caller.from(authentication(Map.of("azp", APP.toString(), "sub", "subject-1"), List.of(Roles.CLIENT)));

        assertEquals(APP, caller.clientId(), "the client id must be the azp claim of the token");
    }

    @Test
    void theSubjectIsTheSubClaim() {
        Caller caller = Caller.from(authentication(Map.of("azp", APP.toString(), "sub", "subject-1"), List.of(Roles.CLIENT)));

        assertEquals("subject-1", caller.subject(), "the subject must be the sub claim of the token");
    }

    @Test
    void anAdminRoleMakesTheCallerAnAdmin() {
        Caller caller = Caller.from(authentication(Map.of("azp", APP.toString()), List.of(Roles.ADMIN)));

        assertTrue(caller.admin(), "a token with Coloeus.Admin must be an admin");
        assertEquals(Set.of(Roles.ADMIN), caller.roles(), "the roles must be the app roles of the token");
    }

    @Test
    void aClientIsNotAnAdmin() {
        Caller caller = Caller.from(authentication(Map.of("azp", APP.toString()), List.of(Roles.CLIENT)));

        assertFalse(caller.admin(), "a Coloeus.Client token must not be an admin");
    }

    @Test
    void aTokenWithoutAUuidAzpAnswers401() {
        ProblemException failure = assertThrows(ProblemException.class,
                () -> Caller.from(authentication(Map.of("azp", "not-a-uuid"), List.of(Roles.CLIENT))),
                "a token whose azp is no client id must be refused");

        assertEquals(ProblemCode.UNAUTHORIZED, failure.code(), "the refusal must be UNAUTHORIZED");
    }

    @Test
    void aTokenWithoutAzpAnswers401() {
        ProblemException failure = assertThrows(ProblemException.class,
                () -> Caller.from(authentication(Map.of(), List.of(Roles.CLIENT))),
                "a token without azp must be refused");

        assertEquals(ProblemCode.UNAUTHORIZED, failure.code(), "the refusal must be UNAUTHORIZED");
    }

    private static Authentication authentication(Map<String, Object> claims, List<String> roles) {
        return Authentication.build("subject-1", roles, claims);
    }
}
