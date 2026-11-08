package net.onelitefeather.coloeus.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.http.HttpMethod;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.onelitefeather.coloeus.auth.Roles;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;
import org.junit.jupiter.api.Test;

/**
 * The claim and role rules of the staff API, with fake claims and no server.
 */
class StaffAccessTest {

    private static final String OID = "00000000-0000-4000-8000-0000000000e1";

    @Test
    void aDelegatedUserTokenIsAccepted() {
        StaffCaller caller = StaffAccess.caller(delegated(), List.of(Roles.ADMIN));

        assertEquals(OID, caller.actor().oid(), "the actor is the oid of the token");
        assertEquals("Alex Admin", caller.actor().displayName(), "the display name is the name of the token");
    }

    @Test
    void aClientCredentialTokenWithoutScopeIsRefused() {
        Map<String, Object> claims = delegated();
        claims.remove("scp");

        ProblemException refusal = assertThrows(ProblemException.class,
                () -> StaffAccess.caller(claims, List.of(Roles.ADMIN)));

        assertEquals(ProblemCode.UNAUTHORIZED, refusal.code(), "an application token must not pass the staff gate");
    }

    @Test
    void aTokenWithAnotherScopeIsRefused() {
        Map<String, Object> claims = delegated();
        claims.put("scp", "User.Read");

        ProblemException refusal = assertThrows(ProblemException.class,
                () -> StaffAccess.caller(claims, List.of(Roles.ADMIN)));

        assertEquals(ProblemCode.UNAUTHORIZED, refusal.code());
    }

    @Test
    void aScopeListWithTheAccessScopeIsAccepted() {
        Map<String, Object> claims = delegated();
        claims.put("scp", List.of("User.Read", StaffAccess.ACCESS_SCOPE));

        assertEquals(OID, StaffAccess.caller(claims, List.of(Roles.MODERATOR)).actor().oid());
    }

    @Test
    void aTokenWithoutOidIsRefused() {
        Map<String, Object> claims = delegated();
        claims.remove("oid");

        ProblemException refusal = assertThrows(ProblemException.class,
                () -> StaffAccess.caller(claims, List.of(Roles.ADMIN)));

        assertEquals(ProblemCode.UNAUTHORIZED, refusal.code());
    }

    @Test
    void anApplicationIdentityTypeIsRefused() {
        Map<String, Object> claims = delegated();
        claims.put("idtyp", "app");

        ProblemException refusal = assertThrows(ProblemException.class,
                () -> StaffAccess.caller(claims, List.of(Roles.ADMIN)));

        assertEquals(ProblemCode.UNAUTHORIZED, refusal.code());
    }

    @Test
    void aUserIdentityTypeIsAccepted() {
        Map<String, Object> claims = delegated();
        claims.put("idtyp", "user");

        assertEquals(OID, StaffAccess.caller(claims, List.of(Roles.ADMIN)).actor().oid());
    }

    @Test
    void aMissingNameLeavesTheDisplayNameEmpty() {
        Map<String, Object> claims = delegated();
        claims.remove("name");

        assertNull(StaffAccess.caller(claims, List.of(Roles.ADMIN)).actor().displayName());
    }

    @Test
    void aModeratorMayRead() {
        StaffCaller moderator = caller(Roles.MODERATOR);

        StaffAccess.require(moderator, HttpMethod.GET);
    }

    @Test
    void anAdminMayRead() {
        StaffCaller admin = caller(Roles.ADMIN);

        StaffAccess.require(admin, HttpMethod.GET);
    }

    @Test
    void aCallerWithoutStaffRoleMayNotRead() {
        ProblemException refusal = assertThrows(ProblemException.class,
                () -> StaffAccess.require(caller("Coloeus.Client"), HttpMethod.GET));

        assertEquals(ProblemCode.FORBIDDEN_ROLE, refusal.code());
    }

    @Test
    void aModeratorMayNotWrite() {
        ProblemException refusal = assertThrows(ProblemException.class,
                () -> StaffAccess.require(caller(Roles.MODERATOR), HttpMethod.POST));

        assertEquals(ProblemCode.FORBIDDEN_ROLE, refusal.code());
    }

    @Test
    void anAdminMayWrite() {
        StaffAccess.require(caller(Roles.ADMIN), HttpMethod.PUT);
    }

    @Test
    void onlyChangingMethodsAreWrites() {
        assertTrue(StaffAccess.isWrite(HttpMethod.POST));
        assertTrue(StaffAccess.isWrite(HttpMethod.PUT));
        assertFalse(StaffAccess.isWrite(HttpMethod.GET));
    }

    private static StaffCaller caller(String role) {
        return StaffAccess.caller(delegated(), List.of(role));
    }

    /** The claims of a delegated admin token of the web client. */
    private static Map<String, Object> delegated() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("scp", StaffAccess.ACCESS_SCOPE);
        claims.put("oid", OID);
        claims.put("name", "Alex Admin");
        return claims;
    }
}
