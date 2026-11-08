package net.onelitefeather.coloeus.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.onelitefeather.coloeus.account.PlayerAccountResponse;
import net.onelitefeather.coloeus.auth.Permission;
import net.onelitefeather.coloeus.currency.CreateCurrencyRequest;
import net.onelitefeather.coloeus.currency.CurrencyService;
import net.onelitefeather.coloeus.money.AmountJson;
import net.onelitefeather.coloeus.support.LedgerApiIntegrationTest;
import net.onelitefeather.coloeus.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@Tag("integration")
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdempotencyApiIntegrationTest extends LedgerApiIntegrationTest {

    private static final UUID CLIENT = TestTokens.CLIENT_APP;
    private static final UUID OTHER_CLIENT = UUID.fromString("00000000-0000-4000-8000-0000000000c2");
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Inject
    CurrencyService currencies;

    @Inject
    IdempotencyCleanup cleanup;

    @BeforeEach
    void defineCoinsAndRegisterBothClients() {
        currencies.create(new CreateCurrencyRequest("COINS", "Coin", "Coins", "C", 2));
        for (UUID client : List.of(CLIENT, OTHER_CLIENT)) {
            registerClient(client);
            grant(client, "COINS", Permission.MINT);
        }
        grant(CLIENT, "COINS", Permission.READ);
    }

    @Test
    void concurrentDuplicateKeysYieldExactlyOneTransaction() throws Exception {
        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch startGate = new CountDownLatch(1);
        List<UUID> ids = new ArrayList<>();
        try {
            List<Future<UUID>> answers = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                answers.add(pool.submit(() -> {
                    startGate.await();
                    return post(CLIENT, "dup-key", mint(ALICE, "10.00")).body().id();
                }));
            }
            startGate.countDown();
            for (Future<UUID> answer : answers) {
                ids.add(answer.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, ids.stream().distinct().count(), "every concurrent duplicate must answer the same transaction");
        assertEquals(1L, transactionCount(), "exactly one transaction may be stored for the key");
        assertEquals("10.00", coins(ALICE), "the duplicates must not credit the player more than once");
    }

    @Test
    void sameKeyFromAnotherClientIsBookedNormally() {
        post(CLIENT, "shared-key", mint(ALICE, "10.00"));

        HttpResponse<TransactionResponse> other = post(OTHER_CLIENT, "shared-key", mint(ALICE, "5.00"));

        assertEquals(HttpStatus.CREATED, other.status(), "a key used by another client must not be a replay");
        assertEquals("15.00", coins(ALICE), "both clients' mints must be booked");
        assertEquals(2L, transactionCount(), "two independent transactions must be stored");
    }

    @Test
    void replayWithinRetentionReturnsTheStoredAnswer() {
        TransactionResponse first = post(CLIENT, "retained", mint(ALICE, "10.00")).body();
        clock.advance(Duration.ofDays(6));

        TransactionResponse replay = post(CLIENT, "retained", mint(ALICE, "10.00")).body();

        assertEquals(first.id(), replay.id(), "a retry six days later must still replay the first answer");
        assertEquals("10.00", coins(ALICE), "the retry must not book again");
    }

    @Test
    void cleanupDeletesOnlyKeysThatExpired() {
        post(CLIENT, "old-key", mint(ALICE, "1.00"));
        clock.advance(Duration.ofDays(3));
        post(CLIENT, "new-key", mint(ALICE, "1.00"));
        clock.advance(Duration.ofDays(5));

        int deleted = cleanup.deleteExpired(clock.instant());

        assertEquals(1, deleted, "only the key stored eight days ago has expired");
        assertEquals(List.of("new-key"), storedKeys(), "the key stored five days ago must remain");
    }

    @Test
    void cleanupDeletesAKeyExactlyAtItsExpiry() {
        post(CLIENT, "expires-now", mint(ALICE, "1.00"));
        clock.advance(Duration.ofDays(7));

        int deleted = cleanup.deleteExpired(clock.instant());

        assertEquals(1, deleted, "a key whose expiry equals now is expired and must be deleted");
        assertEquals(List.of(), storedKeys(), "no key may remain after its expiry");
    }

    /** Posts as the registered client {@code client}, signed with a token of that client's application id. */
    private HttpResponse<TransactionResponse> post(UUID client, String key, String body) {
        MutableHttpRequest<String> request = HttpRequest.POST("/v1/transactions", body)
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .bearerAuth(TOKENS.clientOf(client))
                .header("Idempotency-Key", key);
        return this.client.toBlocking().exchange(request, TransactionResponse.class);
    }

    private String coins(UUID player) {
        PlayerAccountResponse account = client.toBlocking().retrieve(
                get(TOKENS.client(), "/v1/accounts/player/" + player), PlayerAccountResponse.class);
        return account.balances().stream()
                .filter(amount -> amount.currency().equals("COINS"))
                .map(AmountJson::amount)
                .findFirst()
                .orElseThrow();
    }

    private static String mint(UUID player, String amount) {
        return "{\"kind\":\"MINT\",\"to\":{\"player\":\"" + player + "\"},"
                + "\"amount\":{\"amount\":\"" + amount + "\",\"currency\":\"COINS\"}}";
    }

    private static long transactionCount() {
        return query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM transaction")) {
                try (ResultSet rows = statement.executeQuery()) {
                    rows.next();
                    return rows.getLong(1);
                }
            }
        });
    }

    private static List<String> storedKeys() {
        return query(connection -> {
            List<String> keys = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT key FROM idempotency_key ORDER BY key");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    keys.add(rows.getString("key"));
                }
            }
            return keys;
        });
    }
}
