package net.onelitefeather.coloeus.client;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * Connection settings of the Coloeus API: its base URL and the Entra app registration of the caller.
 *
 * @param baseUri      base URL of the Coloeus API, for example {@code https://coloeus.apps.onelite.feather}
 * @param tenantId     the Entra tenant, a GUID or a domain
 * @param clientId     the client id of the caller's app registration
 * @param clientSecret the secret of that app registration; never printed
 * @param scope        the token scope, {@code api://<client id of the Coloeus API>/.default}
 */
public record ColoeusClientConfig(URI baseUri, String tenantId, String clientId, String clientSecret, String scope) {

    private static final Pattern TENANT = Pattern.compile("[A-Za-z0-9.-]+");

    public ColoeusClientConfig {
        if (baseUri == null || !baseUri.isAbsolute() || baseUri.getHost() == null
                || !("https".equals(baseUri.getScheme()) || "http".equals(baseUri.getScheme()))) {
            throw new IllegalArgumentException("url must be an absolute http or https URL");
        }
        requireText(tenantId, "tenant-id");
        requireText(clientId, "client-id");
        requireText(clientSecret, "client-secret");
        requireText(scope, "auth-scope");
        if (!TENANT.matcher(tenantId).matches()) {
            throw new IllegalArgumentException("tenant-id must be a tenant GUID or domain");
        }
    }

    /**
     * @return the Entra token endpoint of the tenant
     */
    public URI tokenEndpoint() {
        return URI.create("https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token");
    }

    /** Never prints the secret. */
    @Override
    public String toString() {
        return "ColoeusClientConfig{baseUri=" + baseUri + ", tenantId=" + tenantId + ", clientId=" + clientId
                + ", clientSecret=<redacted>, scope=" + scope + "}";
    }

    private static void requireText(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " must not be blank");
        }
    }
}
