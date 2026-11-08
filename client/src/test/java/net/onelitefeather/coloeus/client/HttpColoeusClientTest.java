package net.onelitefeather.coloeus.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.money.MonetaryAmount;
import org.javamoney.moneta.Money;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpColoeusClientTest {

    private static final UUID ALEX = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
    private static final UUID ACCOUNT = UUID.fromString("7b1d2d0e-6a3c-4f36-9d55-1e2f3a4b5c6d");
    private static final String TOKEN = "{\"access_token\":\"token-1\",\"expires_in\":3600}";
    private static final String BEARER = "Bearer token-1";
    private static final String COINS = "COINS";
    private static final String TRANSACTIONS = "/v1/transactions";
    /** The per attempt timeout of the client in most tests: long enough that no test relies on it. */
    private static final Duration ATTEMPT = Duration.ofSeconds(5);
    /** A short attempt timeout for the tests that wait for one. */
    private static final Duration SHORT = Duration.ofMillis(250);
    private static final Duration BUDGET = Duration.ofSeconds(10);

    private FakeServer fake;
    private MutableClock clock;
    /** The monotonic time of the client under test; advanced by the tests, never by the wall clock. */
    private final AtomicLong nanos = new AtomicLong();
    private ExecutorService caller;

    @BeforeEach
    void setUp() throws Exception {
        fake = new FakeServer();
        clock = new MutableClock();
        caller = Executors.newSingleThreadExecutor();
        fake.reply("POST", "/token", 200, TOKEN);
    }

    @AfterEach
    void tearDown() {
        caller.shutdownNow();
        fake.close();
    }

    @Test
    void currenciesReturnTheDefaultCurrencyWithTheBearerToken() {
        fake.reply("GET", "/v1/currencies", 200, "[{\"code\":\"COINS\",\"nameSingular\":\"coin\","
                + "\"namePlural\":\"coins\",\"symbol\":\"c\",\"fractionDigits\":2,\"defaultCurrency\":true,"
                + "\"createdAt\":\"2026-01-01T00:00:00Z\",\"updatedAt\":\"2026-01-01T00:00:00Z\"}]");

        List<CurrencyDefinition> currencies = client(ATTEMPT).currencies(BUDGET);

        assertEquals(1, currencies.size(), "the answer must contain the one currency of the fake");
        CurrencyDefinition coins = currencies.get(0);
        assertEquals(COINS, coins.code(), "the currency code must be read from the answer");
        assertEquals(2, coins.fractionDigits(), "the fraction digits must be read from the answer");
        assertTrue(coins.defaultCurrency(), "the default flag must be read from the answer");
        assertEquals(BEARER, fake.requestsTo("GET", "/v1/currencies").get(0).authorization(),
                "every call must carry the bearer token");
    }

    @Test
    void playerByNameReturnsTheUuidOfTheName() {
        fake.reply("GET", "/v1/players/by-name/Alex", 200, "{\"uuid\":\"" + ALEX + "\",\"name\":\"Alex\","
                + "\"lastSeen\":\"2026-01-01T00:00:00Z\",\"source\":\"REPORT\",\"history\":[]}");

        assertEquals(ALEX, client(ATTEMPT).playerByName("Alex", BUDGET), "the UUID of the name must be returned");
    }

    @Test
    void playerByNameThrowsPlayerUnknownForAnUnknownName() {
        fake.reply("GET", "/v1/players/by-name/Nobody123", 404,
                "{\"code\":\"PLAYER_UNKNOWN\",\"status\":404,\"detail\":\"unknown\"}");

        assertThrows(PlayerUnknownException.class, () -> client(ATTEMPT).playerByName("Nobody123", BUDGET),
                "an unknown name must surface as PlayerUnknownException");
    }

    @Test
    void readWithoutAnAnswerInTimeThrowsUnavailable() {
        fake.route("GET", "/v1/currencies", request -> fake.awaitRelease());

        assertThrows(ColoeusUnavailableException.class, () -> client(ATTEMPT).currencies(SHORT),
                "a read without an answer within its timeout must throw ColoeusUnavailableException");
    }

    @Test
    void serverErrorWithoutProblemDocumentThrowsUnavailable() {
        fake.reply("GET", "/v1/currencies", 500, "boom");

        assertThrows(ColoeusUnavailableException.class, () -> client(ATTEMPT).currencies(BUDGET),
                "a 5xx answer without a problem document must throw ColoeusUnavailableException");
    }

    @Test
    void knownProblemCodeThrowsItsTypedException() {
        fake.reply("POST", "/v1/transactions", 422,
                "{\"code\":\"INSUFFICIENT_FUNDS\",\"status\":422,\"detail\":\"balance too low\"}");

        InsufficientFundsException thrown = assertThrows(InsufficientFundsException.class,
                () -> client(ATTEMPT).transact(burn("2.50"), "k-1", BUDGET),
                "INSUFFICIENT_FUNDS must surface as InsufficientFundsException");
        assertEquals(422, thrown.status(), "the typed exception must carry the HTTP status");
    }

    @Test
    void unknownProblemCodeThrowsProblemExceptionWithCodeAndStatus() {
        fake.reply("PUT", "/v1/players/" + ALEX + "/name", 409,
                "{\"code\":\"CURRENCY_EXISTS\",\"status\":409,\"detail\":\"already there\"}");

        ColoeusProblemException thrown = assertThrows(ColoeusProblemException.class,
                () -> client(ATTEMPT).reportName(ALEX, "Alex", "k-2", BUDGET),
                "an unknown problem code must surface as ColoeusProblemException");
        assertEquals(ColoeusProblemException.class, thrown.getClass(),
                "an unknown code must not map to one of the typed exceptions");
        assertEquals("CURRENCY_EXISTS", thrown.code(), "the code of the problem must be kept");
        assertEquals(409, thrown.status(), "the status of the problem must be kept");
    }

    @Test
    void writeSendsTheIdempotencyKeyAndTheBearerToken() {
        fake.reply("POST", "/v1/transactions", 201, transactionAnswer("12.50"));

        client(ATTEMPT).transact(mint("2.50"), "k-1", BUDGET);

        FakeServer.Request write = fake.requestsTo("POST", "/v1/transactions").get(0);
        assertEquals("k-1", write.idempotencyKey(), "the write must carry the idempotency key of the caller");
        assertEquals(BEARER, write.authorization(), "the write must carry the bearer token");
        assertTrue(write.body().contains("\"kind\":\"MINT\""), "a mint must be sent with kind MINT: " + write.body());
        assertTrue(write.body().contains("\"amount\":\"2.5"),
                "the amount must be sent as a decimal string: " + write.body());
    }

    @Test
    void transactionAnswerReturnsTheResultingPlayerBalance() {
        fake.reply("POST", "/v1/transactions", 201, transactionAnswer("12.50"));

        TransactionResult result = client(ATTEMPT).transact(mint("2.50"), "k-1", BUDGET);

        assertEquals(1, result.balances().size(), "the answer must carry the balance of the credited player");
        MonetaryAmount balance = result.balances().get(0).amount();
        assertEquals(0, new BigDecimal("12.50").compareTo(balance.getNumber().numberValue(BigDecimal.class)),
                "the resulting balance must be 12.50");
        assertEquals(COINS, balance.getCurrency().getCurrencyCode(), "the balance must be in the booked currency");
    }

    @Test
    void transferNamesTheDebitedPlayerAsActor() {
        fake.reply("POST", "/v1/transactions", 201, transactionAnswer("1.00"));
        UUID to = UUID.fromString("11111111-2222-3333-4444-555555555555");

        client(ATTEMPT).transact(Transaction.transfer(ALEX, to, amount("1.00"), "pay"), "k-3", BUDGET);

        String body = fake.requestsTo("POST", "/v1/transactions").get(0).body();
        assertTrue(body.contains("\"actor\":\"" + ALEX + "\""),
                "a transfer must name the debited player as actor: " + body);
    }

    @Test
    void playerAccountReturnsTheBalanceOfTheAccount() {
        fake.reply("GET", "/v1/accounts/player/" + ALEX, 200, "{\"id\":\"" + ACCOUNT + "\",\"kind\":\"PLAYER\","
                + "\"owner\":\"" + ALEX + "\",\"balances\":[{\"amount\":\"10.00\",\"currency\":\"COINS\"}]}");

        PlayerAccount account = client(ATTEMPT).playerAccount(ALEX, BUDGET);

        assertEquals(ACCOUNT, account.id(), "the account id must be read from the answer");
        assertEquals(ALEX, account.owner(), "the owner must be read from the answer");
        MonetaryAmount coins = account.balanceIn(COINS).orElseThrow(
                () -> new AssertionError("the balance in COINS must be part of the account"));
        assertEquals(0, new BigDecimal("10.00").compareTo(coins.getNumber().numberValue(BigDecimal.class)),
                "the balance must be 10.00 COINS");
    }

    @Test
    void balancesOfAnAccountAreReturnedInEveryCurrency() {
        fake.reply("GET", "/v1/accounts/" + ACCOUNT + "/balances", 200,
                "{\"accountId\":\"" + ACCOUNT + "\",\"kind\":\"PLAYER\",\"latestPostingId\":null,"
                        + "\"latestPostingAt\":null,\"balances\":[{\"amount\":\"3.00\",\"currency\":\"COINS\"}]}");

        List<MonetaryAmount> balances = client(ATTEMPT).balances(ACCOUNT, BUDGET);

        assertEquals(1, balances.size(), "one balance row of the account must be returned");
        assertEquals(0, new BigDecimal("3.00").compareTo(balances.get(0).getNumber().numberValue(BigDecimal.class)),
                "the balance must be 3.00");
    }

    @Test
    void writeIsRetriedOnceWithTheSameKeyAfterAServerError() {
        AtomicInteger attempts = new AtomicInteger();
        fake.route("POST", "/v1/transactions", request -> attempts.incrementAndGet() == 1
                ? new FakeServer.Reply(503, "")
                : new FakeServer.Reply(201, transactionAnswer("2.50")));

        TransactionResult result = client(ATTEMPT).transact(mint("2.50"), "k-4", BUDGET);

        List<FakeServer.Request> writes = fake.requestsTo("POST", "/v1/transactions");
        assertEquals(2, writes.size(), "a 5xx answer within the budget must be retried once");
        assertEquals("k-4", writes.get(0).idempotencyKey(), "the first attempt must carry the key");
        assertEquals("k-4", writes.get(1).idempotencyKey(), "the retry must reuse the same key");
        assertEquals(1, result.balances().size(), "the answer of the retry must be returned");
    }

    @Test
    void writeWithAClientErrorIsNotRetried() {
        fake.reply("POST", "/v1/transactions", 422,
                "{\"code\":\"INSUFFICIENT_FUNDS\",\"status\":422,\"detail\":\"low\"}");

        assertThrows(InsufficientFundsException.class, () -> client(ATTEMPT).transact(burn("2.50"), "k-5", BUDGET));

        assertEquals(1, fake.requestsTo("POST", "/v1/transactions").size(),
                "a 4xx answer is final and must not be retried");
    }

    @Test
    void writeTimingOutTwiceThrowsUnavailableWithTheSameKey() {
        fake.route("POST", "/v1/transactions", request -> fake.awaitRelease());
        ColoeusClient client = client(SHORT);

        Future<?> call = caller.submit(() -> client.transact(mint("2.50"), "k-6", BUDGET));

        assertEquals("k-6", fake.nextArrival(TRANSACTIONS).idempotencyKey(), "the first attempt must carry the key");
        assertEquals("k-6", fake.nextArrival(TRANSACTIONS).idempotencyKey(), "the retry must carry the same key");
        assertInstanceOf(ColoeusUnavailableException.class, causeOf(call),
                "two timeouts must end the call with ColoeusUnavailableException");
    }

    @Test
    void writeIsRetriedWithTheRemainingBudgetWhenLessThanAFullAttemptIsLeft() {
        AtomicInteger attempts = new AtomicInteger();
        fake.route("POST", "/v1/transactions", request -> {
            if (attempts.incrementAndGet() > 1) {
                return new FakeServer.Reply(201, transactionAnswer("2.50"));
            }
            nanos.addAndGet(Duration.ofMillis(600).toNanos());
            return fake.awaitRelease();
        });
        ColoeusClient client = new HttpColoeusClient(config(), fake.uri().resolve("/token"), HttpClient.newHttpClient(),
                clock, nanos::get, Duration.ofMillis(500));

        TransactionResult result = client.transact(mint("2.50"), "k-8", Duration.ofSeconds(1));

        List<FakeServer.Request> writes = fake.requestsTo("POST", "/v1/transactions");
        assertEquals(2, writes.size(), "after a timed out attempt with 400 ms of a 1 s budget left, the write must be retried");
        assertEquals("k-8", writes.get(0).idempotencyKey(), "the first attempt must carry the key");
        assertEquals("k-8", writes.get(1).idempotencyKey(), "the retry must reuse the same key");
        assertEquals(1, result.balances().size(), "the answer of the retry must be returned");
    }

    @Test
    void writeIsNotRetriedWhenLessThanTheMinimumAttemptIsLeft() {
        fake.route("POST", "/v1/transactions", request -> {
            nanos.addAndGet(Duration.ofMillis(900).toNanos());
            return fake.awaitRelease();
        });
        ColoeusClient client = new HttpColoeusClient(config(), fake.uri().resolve("/token"), HttpClient.newHttpClient(),
                clock, nanos::get, Duration.ofMillis(500));

        Future<?> call = caller.submit(() -> client.transact(mint("2.50"), "k-7", Duration.ofSeconds(1)));

        assertEquals("k-7", fake.nextArrival(TRANSACTIONS).idempotencyKey(), "the first attempt must carry the key");
        assertInstanceOf(ColoeusUnavailableException.class, causeOf(call),
                "the timed out attempt must end the call with ColoeusUnavailableException");
        assertEquals(1, fake.requestsTo("POST", "/v1/transactions").size(),
                "with 100 ms of a 1 s budget left, which is below the 150 ms minimum, another attempt must not be sent");
    }

    @Test
    void rejectedBearerTokenIsDroppedSoTheNextCallFetchesANewOne() {
        fake.reply("GET", "/v1/currencies", 401, "{\"code\":\"UNAUTHORIZED\",\"status\":401,\"detail\":\"expired\"}");
        ColoeusClient client = client(ATTEMPT);

        assertThrows(AuthenticationException.class, () -> client.currencies(BUDGET),
                "a 401 with code UNAUTHORIZED must surface as AuthenticationException");
        fake.reply("GET", "/v1/currencies", 200, "[]");
        fake.reply("POST", "/token", 200, "{\"access_token\":\"token-2\",\"expires_in\":3600}");
        client.currencies(BUDGET);

        assertEquals(2, fake.requestsTo("POST", "/token").size(),
                "after a 401 the client must fetch a new token for the next call");
    }

    private HttpColoeusClient client(Duration attemptTimeout) {
        return new HttpColoeusClient(config(), fake.uri().resolve("/token"), HttpClient.newHttpClient(), clock,
                nanos::get, attemptTimeout);
    }

    private ColoeusClientConfig config() {
        return new ColoeusClientConfig(fake.uri(), "tenant-1", "client-1", "secret-1", "api://coloeus/.default");
    }

    private static Transaction mint(String amount) {
        return Transaction.mint(ALEX, amount(amount), "Vault depositPlayer");
    }

    private static Transaction burn(String amount) {
        return Transaction.burn(ALEX, amount(amount), "Vault withdrawPlayer");
    }

    private static MonetaryAmount amount(String value) {
        return Money.of(new BigDecimal(value), new CurrencyDefinition("COINS", "coin", "coins", "c", 2, true).unit());
    }

    private static String transactionAnswer(String balance) {
        return "{\"id\":\"" + UUID.randomUUID() + "\",\"kind\":\"MINT\",\"createdAt\":\"2026-01-01T00:00:00Z\","
                + "\"reason\":null,\"actor\":null,\"externalReference\":null,\"metadata\":{},"
                + "\"postings\":[],\"balances\":[{\"account\":\"" + ACCOUNT + "\",\"amount\":{\"amount\":\""
                + balance + "\",\"currency\":\"COINS\"}}]}";
    }

    /** The failure of a call that must have failed; waits for the call, never for a clock. */
    private static Throwable causeOf(Future<?> call) {
        try {
            call.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            return e.getCause();
        } catch (InterruptedException | TimeoutException e) {
            throw new AssertionError("the call did not finish in time", e);
        }
        throw new AssertionError("the call was expected to fail but returned normally");
    }
}
