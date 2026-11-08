package net.onelitefeather.coloeus.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenProviderTest {

    private static final Duration TIMEOUT = Duration.ofMillis(500);
    private static final String TOKEN_1 = "{\"access_token\":\"token-1\",\"expires_in\":3600}";

    private FakeServer fake;
    private MutableClock clock;
    private ExecutorService threads;

    @BeforeEach
    void setUp() throws Exception {
        fake = new FakeServer();
        clock = new MutableClock();
        threads = Executors.newFixedThreadPool(20);
    }

    @AfterEach
    void tearDown() {
        threads.shutdownNow();
        fake.close();
    }

    @Test
    void reusesTheTokenWhileItIsFarFromExpiry() {
        fake.reply("POST", "/token", 200, TOKEN_1);
        TokenProvider provider = provider();

        assertEquals("token-1", provider.token(TIMEOUT), "first call must return the fetched token");
        clock.advance(Duration.ofMinutes(10));
        assertEquals("token-1", provider.token(TIMEOUT), "a call ten minutes later must reuse the token");

        assertEquals(1, fake.requestsTo("POST", "/token").size(),
                "the token endpoint must be called once while the token is valid");
    }

    @Test
    void fetchesANewTokenOnceTheCachedOneIsWithinSixtySecondsOfExpiry() {
        fake.reply("POST", "/token", 200, TOKEN_1);
        TokenProvider provider = provider();
        provider.token(TIMEOUT);

        clock.advance(Duration.ofSeconds(3541));
        fake.reply("POST", "/token", 200, "{\"access_token\":\"token-2\",\"expires_in\":3600}");

        assertEquals("token-2", provider.token(TIMEOUT), "a token 59 seconds from expiry must be replaced");
        assertEquals(2, fake.requestsTo("POST", "/token").size(), "a second token request must have been sent");
    }

    @Test
    void concurrentCallsAtExpiryShareOneTokenRequest() throws Exception {
        fake.reply("POST", "/token", 200, TOKEN_1);
        TokenProvider provider = provider();
        int callers = 20;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            Callable<String> call = () -> {
                start.await();
                return provider.token(TIMEOUT);
            };
            results.add(threads.submit(call));
        }

        start.countDown();
        for (Future<String> result : results) {
            assertEquals("token-1", result.get(10, TimeUnit.SECONDS), "every caller must receive the one token");
        }
        assertEquals(1, fake.requestsTo("POST", "/token").size(),
                "twenty concurrent callers must cause one token request");
    }

    @Test
    void aRejectedTokenIsNotReturnedAgain() {
        fake.reply("POST", "/token", 200, TOKEN_1);
        TokenProvider provider = provider();
        provider.token(TIMEOUT);

        provider.rejected("token-1");
        fake.reply("POST", "/token", 200, "{\"access_token\":\"token-2\",\"expires_in\":3600}");

        assertEquals("token-2", provider.token(TIMEOUT), "after a rejection the next call must fetch a new token");
    }

    @Test
    void refusedClientCredentialsThrowAuthenticationException() {
        fake.reply("POST", "/token", 401, "{\"error\":\"invalid_client\"}");
        TokenProvider provider = provider();

        assertThrows(AuthenticationException.class, () -> provider.token(TIMEOUT),
                "a 401 from the identity provider must surface as AuthenticationException");
    }

    @Test
    void identityProviderErrorThrowsUnavailable() {
        fake.reply("POST", "/token", 503, "");
        TokenProvider provider = provider();

        assertThrows(ColoeusUnavailableException.class, () -> provider.token(TIMEOUT),
                "a 503 from the identity provider must surface as ColoeusUnavailableException");
    }

    private TokenProvider provider() {
        ColoeusClientConfig config = new ColoeusClientConfig(fake.uri(), "tenant-1", "client-1", "secret-1",
                "api://coloeus/.default");
        return new TokenProvider(config, fake.uri().resolve("/token"), HttpClient.newHttpClient(), clock);
    }
}
