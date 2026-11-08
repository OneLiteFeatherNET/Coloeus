package net.onelitefeather.coloeus.admin;

import static net.onelitefeather.coloeus.support.ProblemAssertions.assertProblem;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.support.Api;
import net.onelitefeather.coloeus.support.LedgerApiIntegrationTest;
import net.onelitefeather.coloeus.support.TestTokens;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * The gate of {@code /v1/staff}: the delegated staff token, the role of the method and the refusals, through the HTTP
 * stack. The probe route only answers, so the gate is the only rule that decides.
 */
@Tag("integration")
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StaffGateIntegrationTest extends LedgerApiIntegrationTest {

    private static final String PROBE = "/v1/staff/probe";

    @Test
    void aCallWithoutTokenIsRefusedWithUnauthorized() {
        HttpClientResponseException refusal = Api.refusal(client, HttpRequest.GET(PROBE));

        assertProblem(refusal, HttpStatus.UNAUTHORIZED, ProblemCode.UNAUTHORIZED);
    }

    @Test
    void aClientCredentialTokenIsRefusedWithUnauthorized() {
        HttpClientResponseException refusal = Api.refusal(client,
                HttpRequest.GET(PROBE).bearerAuth(TOKENS.admin()));

        assertProblem(refusal, HttpStatus.UNAUTHORIZED, ProblemCode.UNAUTHORIZED);
    }

    @Test
    void aClientCredentialTokenIsRefusedOnAWriteToo() {
        HttpClientResponseException refusal = Api.refusal(client,
                HttpRequest.POST(PROBE, "{}").bearerAuth(TOKENS.admin()));

        assertProblem(refusal, HttpStatus.UNAUTHORIZED, ProblemCode.UNAUTHORIZED);
    }

    @Test
    void aModeratorMayRead() {
        String body = client.toBlocking().retrieve(Api.bearer(HttpRequest.GET(PROBE), TOKENS.staffModerator()));

        assertEquals("ok", body, "a moderator must pass the read gate");
    }

    @Test
    void anAdminMayWrite() {
        String body = client.toBlocking().retrieve(Api.bearer(HttpRequest.POST(PROBE, "{}"), TOKENS.staffAdmin()));

        assertEquals("ok", body, "an admin must pass the write gate");
    }

    @Test
    void aModeratorWriteIsRefusedBeforeTheBodyIsRead() {
        HttpClientResponseException refusal = Api.refusal(client,
                Api.bearer(HttpRequest.POST(PROBE, "this is not JSON"), TOKENS.staffModerator()));

        assertProblem(refusal, HttpStatus.FORBIDDEN, ProblemCode.FORBIDDEN_ROLE);
    }

    @Test
    void aTokenWithoutTheAccessScopeIsRefused() {
        String token = TOKENS.token().azp(TestTokens.WEB_APP).claim("oid", TestTokens.STAFF_ADMIN_OID)
                .roles(TestTokens.Roles.ADMIN).sign();

        HttpClientResponseException refusal = Api.refusal(client, Api.bearer(HttpRequest.GET(PROBE), token));

        assertProblem(refusal, HttpStatus.UNAUTHORIZED, ProblemCode.UNAUTHORIZED);
    }

    @Test
    void aDelegatedTokenWithoutRoleMayNotRead() {
        String token = TOKENS.staff(TestTokens.STAFF_ADMIN_OID, "Alex Admin", "Coloeus.Client");

        HttpClientResponseException refusal = Api.refusal(client, Api.bearer(HttpRequest.GET(PROBE), token));

        assertProblem(refusal, HttpStatus.FORBIDDEN, ProblemCode.FORBIDDEN_ROLE);
    }
}
