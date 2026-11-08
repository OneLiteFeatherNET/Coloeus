package net.onelitefeather.coloeus.paper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.onelitefeather.coloeus.client.ColoeusClientConfig;

/**
 * The settings of the plugin. Each value comes from {@code config.yml}; the environment variable of the same meaning
 * (upper snake case with a {@code COLOEUS_} prefix) wins when it is set and not blank.
 *
 * @param url          the base URL of the Coloeus API, or null when not configured
 * @param tenantId     the Entra tenant, or null when not configured
 * @param clientId     the client id of the server's app registration, or null when not configured
 * @param clientSecret the secret of that app registration, or null when not configured
 * @param authScope    the token scope; defaults to the scope of the Coloeus API
 */
record ConnectionSettings(String url, String tenantId, String clientId, String clientSecret, String authScope) {

    static final String DEFAULT_AUTH_SCOPE = "api://e6ba3e14-7b03-4792-b6aa-d963f776bd0c/.default";

    /**
     * @param file the value of a config key, null when absent
     * @param env  the value of an environment variable, null when absent
     * @return the settings with environment variables taking precedence
     */
    static ConnectionSettings resolve(Function<String, String> file, Function<String, String> env) {
        return new ConnectionSettings(
                value(file, env, "url", "COLOEUS_URL", null),
                value(file, env, "tenant-id", "COLOEUS_TENANT_ID", null),
                value(file, env, "client-id", "COLOEUS_CLIENT_ID", null),
                value(file, env, "client-secret", "COLOEUS_CLIENT_SECRET", null),
                value(file, env, "auth-scope", "COLOEUS_AUTH_SCOPE", DEFAULT_AUTH_SCOPE));
    }

    /**
     * @return one entry per required value that is missing, naming the config key and its environment variable
     */
    List<String> missing() {
        List<String> missing = new ArrayList<>();
        if (isBlank(url)) {
            missing.add("url (COLOEUS_URL)");
        }
        if (isBlank(tenantId)) {
            missing.add("tenant-id (COLOEUS_TENANT_ID)");
        }
        if (isBlank(clientId)) {
            missing.add("client-id (COLOEUS_CLIENT_ID)");
        }
        if (isBlank(clientSecret)) {
            missing.add("client-secret (COLOEUS_CLIENT_SECRET)");
        }
        return missing;
    }

    /**
     * @return the client configuration
     * @throws IllegalArgumentException when a value is not valid, for example a URL that does not parse
     */
    ColoeusClientConfig toClientConfig() {
        return new ColoeusClientConfig(URI.create(url), tenantId, clientId, clientSecret, authScope);
    }

    /** Never prints the secret. */
    @Override
    public String toString() {
        return "ConnectionSettings{url=" + url + ", tenantId=" + tenantId + ", clientId=" + clientId
                + ", clientSecret=" + (isBlank(clientSecret) ? "<unset>" : "<redacted>")
                + ", authScope=" + authScope + "}";
    }

    private static String value(Function<String, String> file, Function<String, String> env, String key,
                                String variable, String fallback) {
        String fromEnv = env.apply(variable);
        if (!isBlank(fromEnv)) {
            return fromEnv.strip();
        }
        String fromFile = file.apply(key);
        if (!isBlank(fromFile)) {
            return fromFile.strip();
        }
        return fallback;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
