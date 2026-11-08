package net.onelitefeather.coloeus.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Gets Entra access tokens with the OAuth 2.0 client-credentials grant and keeps the current one until 60 seconds
 * before it expires. A caller that arrives during a fetch waits for that fetch instead of starting its own.
 */
final class TokenProvider {

    /** A token is replaced this long before it expires. */
    static final Duration SKEW = Duration.ofSeconds(60);

    private final URI endpoint;
    private final String form;
    private final HttpClient http;
    private final Clock clock;

    private String token;
    private Instant refreshAt = Instant.MIN;

    TokenProvider(ColoeusClientConfig config, URI endpoint, HttpClient http, Clock clock) {
        this.endpoint = endpoint;
        this.http = http;
        this.clock = clock;
        this.form = "grant_type=client_credentials"
                + "&client_id=" + encode(config.clientId())
                + "&client_secret=" + encode(config.clientSecret())
                + "&scope=" + encode(config.scope());
    }

    /**
     * @param timeout the time the token request may take
     * @return a token that is valid now
     * @throws AuthenticationException      when the identity provider refuses the client credentials
     * @throws ColoeusUnavailableException  when the request times out, fails or the identity provider is down
     */
    synchronized String token(Duration timeout) {
        if (token != null && clock.instant().isBefore(refreshAt)) {
            return token;
        }
        return fetch(timeout);
    }

    /**
     * Tells the provider that the server refused this token, so the next {@link #token(Duration)} fetches a new one.
     */
    synchronized void rejected(String rejected) {
        // A caller that reports late must not drop a newer token another caller already fetched.
        if (rejected.equals(token)) {
            token = null;
            refreshAt = Instant.MIN;
        }
    }

    private String fetch(Duration timeout) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new ColoeusUnavailableException("token request to " + endpoint.getHost() + " timed out", e);
        } catch (IOException e) {
            throw new ColoeusUnavailableException("token request to " + endpoint.getHost() + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ColoeusUnavailableException("token request interrupted", e);
        }
        int status = response.statusCode();
        if (status / 100 == 4) {
            throw new AuthenticationException(status, "the identity provider refused the client credentials");
        }
        if (status / 100 != 2) {
            throw new ColoeusUnavailableException("token endpoint answered HTTP " + status);
        }
        JsonObject body;
        try {
            body = JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new ColoeusException("token answer is not a JSON object", e);
        }
        String accessToken = stringOf(body, "access_token");
        long expiresIn = longOf(body, "expires_in");
        if (accessToken.isEmpty() || expiresIn <= 0) {
            throw new ColoeusException("token answer has no access_token or expires_in");
        }
        this.token = accessToken;
        this.refreshAt = clock.instant().plusSeconds(expiresIn).minus(SKEW);
        return accessToken;
    }

    private static String stringOf(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static long longOf(JsonObject object, String name) {
        JsonElement value = object.get(name);
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsLong() : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
