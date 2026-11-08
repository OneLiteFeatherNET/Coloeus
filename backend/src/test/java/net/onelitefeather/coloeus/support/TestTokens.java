package net.onelitefeather.coloeus.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Signs access tokens with a local RSA key and serves its public half on a loopback JWKS endpoint, so the service
 * validates tokens the way it does against Entra, without Entra and without internet access. One instance per test
 * class; close it in {@code @AfterAll}.
 *
 * <p>Valid tokens expire in 2100 and expired ones in 2000: Micronaut checks {@code exp} against the real clock, so the
 * fixture must not depend on when the tests run.
 */
public final class TestTokens implements AutoCloseable {

    public static final String TENANT = "00000000-0000-4000-8000-0000000000aa";
    public static final String AUDIENCE = "coloeus-test-api";
    public static final String ISSUER = "https://login.microsoftonline.com/" + TENANT + "/v2.0";

    /** The Entra application ids (the {@code azp} of the tokens) the tests use. */
    public static final UUID CLIENT_APP = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    public static final UUID ADMIN_APP = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    public static final UUID MODERATOR_APP = UUID.fromString("00000000-0000-4000-8000-0000000000d1");

    private static final long VALID_UNTIL = 4_102_444_800L;
    private static final long EXPIRED_AT = 946_684_800L;
    private static final long ISSUED_AT = 1_767_225_600L;

    private final HttpServer jwks;
    private final String jwksUrl;
    private final RSAKey key;

    private TestTokens(HttpServer jwks, RSAKey key) {
        this.jwks = jwks;
        this.key = key;
        this.jwksUrl = "http://" + jwks.getAddress().getAddress().getHostAddress() + ":"
                + jwks.getAddress().getPort() + "/jwks";
    }

    public static TestTokens start() {
        try {
            RSAKey key = new RSAKeyGenerator(2048).keyID("test-key").generate();
            byte[] keys = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/jwks", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, keys.length);
                try (OutputStream body = exchange.getResponseBody()) {
                    body.write(keys);
                }
            });
            server.start();
            return new TestTokens(server, key);
        } catch (JOSEException | IOException e) {
            throw new IllegalStateException("cannot start the test identity provider", e);
        }
    }

    /** The settings that point the service at this fixture instead of Entra. */
    public Map<String, String> properties() {
        return Map.of(
                "coloeus.auth.tenant-id", TENANT,
                "coloeus.auth.audience", AUDIENCE,
                "micronaut.security.token.jwt.signatures.jwks.entra.url", jwksUrl,
                "micronaut.security.token.jwt.claims-validators.issuer", ISSUER,
                "micronaut.security.token.jwt.claims-validators.audience", AUDIENCE);
    }

    /** A valid token of the default client application with the {@code Coloeus.Client} role. */
    public String client() {
        return token().roles(Roles.CLIENT).sign();
    }

    /** A valid token of the given application with the {@code Coloeus.Client} role. */
    public String clientOf(UUID app) {
        return token().azp(app).roles(Roles.CLIENT).sign();
    }

    public String admin() {
        return token().azp(ADMIN_APP).roles(Roles.ADMIN).sign();
    }

    public String moderator() {
        return token().azp(MODERATOR_APP).roles(Roles.MODERATOR).sign();
    }

    /** A token under construction, starting from a valid client token. */
    public Token token() {
        return new Token(key);
    }

    @Override
    public void close() {
        jwks.stop(0);
    }

    /** The app roles as the tokens carry them. */
    public static final class Roles {
        public static final String CLIENT = "Coloeus.Client";
        public static final String ADMIN = "Coloeus.Admin";
        public static final String MODERATOR = "Coloeus.Moderator";

        private Roles() {
        }
    }

    /** Builder for one token. Times are epoch seconds. */
    public static final class Token {

        private final Map<String, Object> claims = new LinkedHashMap<>();
        private RSAKey signingKey;

        private Token(RSAKey key) {
            this.signingKey = key;
            claim("iss", ISSUER);
            claim("aud", AUDIENCE);
            claim("sub", "00000000-0000-4000-8000-0000000000e1");
            claim("azp", CLIENT_APP.toString());
            claim("ver", "2.0");
            claim("iat", ISSUED_AT);
            claim("exp", VALID_UNTIL);
        }

        public Token claim(String name, Object value) {
            this.claims.put(name, value);
            return this;
        }

        public Token issuer(String issuer) {
            return claim("iss", issuer);
        }

        public Token audience(String audience) {
            return claim("aud", audience);
        }

        public Token azp(UUID app) {
            return claim("azp", app.toString());
        }

        public Token roles(String... roles) {
            return claim("roles", List.of(roles));
        }

        public Token expired() {
            return claim("exp", EXPIRED_AT);
        }

        /** Signs with a key that the service does not trust. */
        public Token signedByAnotherKey() {
            try {
                this.signingKey = new RSAKeyGenerator(2048).keyID("other-key").generate();
            } catch (JOSEException e) {
                throw new IllegalStateException("cannot generate a second key", e);
            }
            return this;
        }

        public String sign() {
            try {
                JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
                this.claims.forEach(builder::claim);
                SignedJWT jwt = new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(this.signingKey.getKeyID()).build(),
                        builder.build());
                jwt.sign(new RSASSASigner(this.signingKey));
                return jwt.serialize();
            } catch (JOSEException e) {
                throw new IllegalStateException("cannot sign the test token", e);
            }
        }
    }
}
