package net.onelitefeather.coloeus.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import io.micronaut.context.ApplicationContext;
import java.util.HashMap;
import java.util.Map;
import net.onelitefeather.coloeus.support.PostgresTestContainer;
import net.onelitefeather.coloeus.support.TestDatabase;
import net.onelitefeather.coloeus.support.TestTokens;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The service refuses to start without its Entra settings, and the failure names the variable to set.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RequiredSettingsIntegrationTest {

    private static final PostgreSQLContainer POSTGRES = PostgresTestContainer.start();
    private static final TestTokens TOKENS = TestTokens.start();

    @AfterAll
    static void stopIdentityProvider() {
        TOKENS.close();
    }

    @Test
    void startupFailsNamingTheTenantVariableWhenItIsEmpty() {
        Map<String, Object> settings = withoutSetting("coloeus.auth.tenant-id");

        Throwable failure = assertThrows(Throwable.class, () -> ApplicationContext.builder().properties(settings).start(),
                "the service must not start without a tenant id");

        assertMentions(failure, "COLOEUS_TENANT_ID");
    }

    @Test
    void startupFailsNamingTheAudienceVariableWhenItIsEmpty() {
        Map<String, Object> settings = withoutSetting("coloeus.auth.audience");

        Throwable failure = assertThrows(Throwable.class, () -> ApplicationContext.builder().properties(settings).start(),
                "the service must not start without an audience");

        assertMentions(failure, "COLOEUS_AUDIENCE");
    }

    private static Map<String, Object> withoutSetting(String name) {
        Map<String, Object> settings = new HashMap<>(TestDatabase.properties(POSTGRES, TOKENS));
        settings.put(name, "");
        return settings;
    }

    private static void assertMentions(Throwable failure, String variable) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(variable)) {
                return;
            }
        }
        fail("the startup failure must name " + variable + ", but was: " + failure);
    }
}
