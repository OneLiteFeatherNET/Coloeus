package net.onelitefeather.coloeus.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RequiredSettingsGuardTest {

    @Test
    void aBlankTenantNamesTheVariable() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> RequiredSettingsGuard.require("COLOEUS_TENANT_ID", "  "),
                "a blank tenant id must stop the start-up");

        assertTrue(failure.getMessage().contains("COLOEUS_TENANT_ID"), "the message must name the variable");
    }

    @Test
    void anUnresolvedPlaceholderCountsAsMissing() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> RequiredSettingsGuard.require("COLOEUS_AUDIENCE", "${COLOEUS_AUDIENCE}"),
                "a placeholder left unresolved means the variable is not set");

        assertTrue(failure.getMessage().contains("COLOEUS_AUDIENCE"), "the message must name the variable");
    }

    @Test
    void aSetValuePasses() {
        assertDoesNotThrow(() -> RequiredSettingsGuard.require("COLOEUS_TENANT_ID", "00000000-0000-0000-0000-000000000001"),
                "a set value must pass the guard");
    }
}
