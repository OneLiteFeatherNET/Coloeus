package net.onelitefeather.coloeus.config;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.ConfigurationException;

/**
 * Refuses to start without the settings that have no default: the Entra tenant and the API audience. The message names
 * the environment variable, so the operator sees what to set.
 *
 * <p>A missing variable leaves its YAML placeholder unresolved. Micronaut then fails with a message about the
 * placeholder, or injects nothing, so the guard reads the settings itself and reports the variable.
 */
@Context
public class RequiredSettingsGuard {

    static final String TENANT_VARIABLE = "COLOEUS_TENANT_ID";
    static final String AUDIENCE_VARIABLE = "COLOEUS_AUDIENCE";

    public RequiredSettingsGuard(Environment environment) {
        require(TENANT_VARIABLE, setting(environment, "coloeus.auth.tenant-id"));
        require(AUDIENCE_VARIABLE, setting(environment, "coloeus.auth.audience"));
    }

    /** The value of a setting, or null when it is missing or its placeholder cannot be resolved. */
    private static String setting(Environment environment, String key) {
        try {
            return environment.getProperty(key, String.class).orElse(null);
        } catch (ConfigurationException unresolved) {
            return null;
        }
    }

    /**
     * @throws IllegalStateException naming {@code variable} when {@code value} is missing, blank or an unresolved placeholder
     */
    static void require(String variable, String value) {
        if (value == null || value.isBlank() || value.contains("${")) {
            throw new IllegalStateException("The required setting " + variable + " is not set. Set the environment "
                    + "variable " + variable + " before starting the service (local values: application-dev.yml).");
        }
    }
}
