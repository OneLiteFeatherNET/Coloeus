package net.onelitefeather.coloeus.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ConnectionSettingsTest {

    private static final String TEST_URL = "https://test.coloeus.example";
    private static final String PROD_URL = "https://coloeus.apps.onelite.feather";
    private static final String SECRET = "file-secret-value";

    @Test
    void environmentValueWinsOverTheConfigFile() {
        Map<String, String> file = Map.of("url", TEST_URL);
        Map<String, String> env = Map.of("COLOEUS_URL", PROD_URL);

        ConnectionSettings settings = resolve(file, env);

        assertEquals(PROD_URL, settings.url(), "COLOEUS_URL must win over the url of config.yml");
    }

    @Test
    void blankEnvironmentValueFallsBackToTheConfigFile() {
        Map<String, String> file = Map.of("url", TEST_URL);
        Map<String, String> env = Map.of("COLOEUS_URL", "   ");

        assertEquals(TEST_URL, resolve(file, env).url(), "a blank variable must not override the file");
    }

    @Test
    void defaultScopeIsUsedWhenNeitherSourceSetsOne() {
        ConnectionSettings settings = resolve(Map.of(), Map.of());

        assertEquals(ConnectionSettings.DEFAULT_AUTH_SCOPE, settings.authScope(),
                "without a scope the default scope of the Coloeus API must be used");
    }

    @Test
    void environmentScopeWinsOverTheDefault() {
        ConnectionSettings settings = resolve(Map.of(), Map.of("COLOEUS_AUTH_SCOPE", "api://other/.default"));

        assertEquals("api://other/.default", settings.authScope(), "COLOEUS_AUTH_SCOPE must win over the default");
    }

    @Test
    void missingSecretIsNamedWithItsKeyAndVariable() {
        ConnectionSettings settings = resolve(Map.of("url", TEST_URL, "tenant-id", "tenant-1", "client-id", "c-1"),
                Map.of());

        assertEquals(List.of("client-secret (COLOEUS_CLIENT_SECRET)"), settings.missing(),
                "only the missing secret must be named, with its key and its variable");
    }

    @Test
    void secretFromTheEnvironmentSatisfiesTheRequirement() {
        ConnectionSettings settings = resolve(Map.of("url", TEST_URL, "tenant-id", "tenant-1", "client-id", "c-1"),
                Map.of("COLOEUS_CLIENT_SECRET", "env-secret"));

        assertTrue(settings.missing().isEmpty(), "a secret from the environment must count as configured");
    }

    @Test
    void everyMissingRequiredValueIsListed() {
        List<String> missing = resolve(Map.of(), Map.of()).missing();

        assertEquals(4, missing.size(), "url, tenant, client id and secret must all be reported: " + missing);
    }

    @Test
    void toStringDoesNotPrintTheSecret() {
        ConnectionSettings settings = resolve(Map.of("client-secret", SECRET), Map.of());

        assertFalse(settings.toString().contains(SECRET), "the secret must never appear in a log line");
    }

    @Test
    void unparseableUrlIsRejectedWhenTheClientConfigIsBuilt() {
        ConnectionSettings settings = resolve(Map.of("url", "not a url", "tenant-id", "tenant-1",
                "client-id", "c-1", "client-secret", SECRET), Map.of());

        assertThrows(IllegalArgumentException.class, settings::toClientConfig,
                "a URL that does not parse must be refused before any call");
    }

    private static ConnectionSettings resolve(Map<String, String> file, Map<String, String> env) {
        Function<String, String> fileLookup = new HashMap<>(file)::get;
        Function<String, String> envLookup = new HashMap<>(env)::get;
        return ConnectionSettings.resolve(fileLookup, envLookup);
    }
}
