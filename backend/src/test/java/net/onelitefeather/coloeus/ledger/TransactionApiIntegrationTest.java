package net.onelitefeather.coloeus.ledger;

import static net.onelitefeather.coloeus.support.ProblemAssertions.assertProblem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import net.onelitefeather.coloeus.auth.Permission;
import net.onelitefeather.coloeus.currency.CreateCurrencyRequest;
import net.onelitefeather.coloeus.currency.CurrencyService;
import net.onelitefeather.coloeus.money.AmountJson;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.support.LedgerApiIntegrationTest;
import net.onelitefeather.coloeus.support.TestTokens;
import net.onelitefeather.coloeus.account.PlayerAccountResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * The booking rules of {@code POST /v1/transactions}, sent by a registered client that holds every grant on COINS.
 * Who may book what without those grants is in {@link TransactionAuthorizationIntegrationTest}.
 */
@Tag("integration")
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransactionApiIntegrationTest extends LedgerApiIntegrationTest {

    static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    static final UUID CAROL = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    static final UUID DAVE = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    @Inject
    CurrencyService currencies;

    @BeforeEach
    void defineCoinsAndRegisterTheClient() {
        currencies.create(new CreateCurrencyRequest("COINS", "Coin", "Coins", "C", 2));
        registerClient(TestTokens.CLIENT_APP);
        grant(TestTokens.CLIENT_APP, "COINS", Permission.MINT);
        grant(TestTokens.CLIENT_APP, "COINS", Permission.BURN);
        grant(TestTokens.CLIENT_APP, "COINS", Permission.TRANSFER_ON_BEHALF);
        grant(TestTokens.CLIENT_APP, "COINS", Permission.READ);
        registerClient(TestTokens.ADMIN_APP);
    }

    @Test
    void mintCreditsThePlayerAndBooksTwoPostings() {
        HttpResponse<TransactionResponse> response = post("mint-alice", mint(ALICE, "10.00"));

        assertEquals(HttpStatus.CREATED, response.status(), "a booked transaction must be answered 201 Created");
        assertEquals("10.00", coins(ALICE), "a mint must credit the player");
        assertEquals(2, response.body().postings().size(), "a mint must book exactly two postings");
    }

    @Test
    void burnDebitsThePlayer() {
        post("mint-alice", mint(ALICE, "10.00"));

        post("burn-alice", burn(ALICE, "4.00"));

        assertEquals("6.00", coins(ALICE), "a burn must debit the player");
    }

    @Test
    void transferMovesMoneyBetweenPlayers() {
        post("mint-alice", mint(ALICE, "10.00"));

        HttpResponse<TransactionResponse> response = post("transfer-1", transfer(ALICE, BOB, "4.00"));

        assertEquals(HttpStatus.CREATED, response.status(), "a transfer must be answered 201 Created");
        assertEquals("6.00", coins(ALICE), "the payer must be debited");
        assertEquals("4.00", coins(BOB), "the payee must be credited");
        assertEquals(2, response.body().balances().size(),
                "the answer must carry the resulting balances of both touched player accounts");
    }

    @Test
    void transferBeyondTheBalanceAnswers422InsufficientFunds() {
        post("mint-alice", mint(ALICE, "3.00"));

        HttpClientResponseException problem = assertThrows(HttpClientResponseException.class,
                () -> post("transfer-too-much", transfer(ALICE, BOB, "4.00")),
                "a transfer above the payer's balance must be refused");

        assertProblem(problem, HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.INSUFFICIENT_FUNDS);
        assertEquals("3.00", coins(ALICE), "a refused transfer must leave the payer's balance unchanged");
        assertEquals("0.00", coins(BOB), "a refused transfer must not credit the payee");
    }

    @Test
    void burnBeyondTheBalanceAnswers422InsufficientFunds() {
        post("mint-alice", mint(ALICE, "1.00"));

        HttpClientResponseException problem = assertThrows(HttpClientResponseException.class,
                () -> post("burn-too-much", burn(ALICE, "2.00")),
                "a burn above the player's balance must be refused");

        assertProblem(problem, HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.INSUFFICIENT_FUNDS);
        assertEquals("1.00", coins(ALICE), "a refused burn must leave the balance unchanged");
    }

    @Test
    void unbalancedBatchAnswers422UnbalancedAndStoresNothing() {
        post("mint-alice", mint(ALICE, "5.00"));
        long transactionsBefore = transactionCount();

        HttpClientResponseException problem = assertThrows(HttpClientResponseException.class,
                () -> post("batch-unbalanced", batch(
                        posting(ALICE, "DEBIT", "1.00"),
                        posting(BOB, "CREDIT", "2.00"))),
                "a batch whose postings do not sum to zero must be refused");

        assertProblem(problem, HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.UNBALANCED);
        assertEquals(transactionsBefore, transactionCount(), "an unbalanced batch must store no transaction");
    }

    @Test
    void oneFailingBatchLegRollsBackEveryLeg() {
        // The batch debits two players, which one client may not do on behalf of both: the admin books it.
        post("mint-alice", mint(ALICE, "10.00"));
        long transactionsBefore = transactionCount();

        HttpClientResponseException problem = assertThrows(HttpClientResponseException.class,
                () -> postAs(TOKENS.admin(), "batch-failing-leg", batch(
                        posting(ALICE, "DEBIT", "2.00"),
                        posting(BOB, "CREDIT", "2.00"),
                        posting(CAROL, "DEBIT", "5.00"),
                        posting(DAVE, "CREDIT", "5.00"))),
                "a batch with one leg above its payer's balance must be refused");

        assertProblem(problem, HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.INSUFFICIENT_FUNDS);
        assertEquals("10.00", coins(ALICE), "the valid leg of the refused batch must not be booked");
        assertEquals("0.00", coins(BOB), "the valid credit of the refused batch must not be booked");
        assertEquals(transactionsBefore, transactionCount(), "a refused batch must store no transaction");
    }

    @Test
    void transferFromAndToTheSameAccountIsRejected() {
        post("mint-alice", mint(ALICE, "10.00"));

        HttpClientResponseException problem = assertThrows(HttpClientResponseException.class,
                () -> post("transfer-self", transfer(ALICE, ALICE, "1.00")),
                "a transfer from an account to itself must be refused");

        assertProblem(problem, HttpStatus.BAD_REQUEST, ProblemCode.REQUEST_INVALID);
    }

    @Test
    void sourceAccountCannotBeNamedInATransfer() {
        HttpResponse<TransactionResponse> minted = post("mint-alice", mint(ALICE, "10.00"));
        UUID source = minted.body().postings().get(0).account();

        HttpClientResponseException problem = assertThrows(HttpClientResponseException.class,
                () -> post("transfer-from-source", transferFromAccount(source, BOB, "1.00")),
                "a transfer must not draw on a SOURCE account");

        assertProblem(problem, HttpStatus.FORBIDDEN, ProblemCode.ACCOUNT_FORBIDDEN);
    }

    @Test
    void missingIdempotencyKeyAnswers400AndBooksNothing() {
        long transactionsBefore = transactionCount();

        HttpClientResponseException problem = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(request(TOKENS.client(), null, mint(ALICE, "10.00"))),
                "a write without Idempotency-Key must be refused");

        assertProblem(problem, HttpStatus.BAD_REQUEST, ProblemCode.IDEMPOTENCY_KEY_MISSING);
        assertEquals(transactionsBefore, transactionCount(), "a refused write must store no transaction");
    }

    @Test
    void repeatedRequestWithTheSameKeyReturnsTheStoredAnswerWithoutBookingAgain() {
        HttpResponse<TransactionResponse> first = post("mint-once", mint(ALICE, "10.00"));

        HttpResponse<TransactionResponse> second = post("mint-once", mint(ALICE, "10.00"));

        assertEquals(first.body().id(), second.body().id(), "the replay must answer the original transaction");
        assertEquals(HttpStatus.CREATED, second.status(), "the replay must keep the original status");
        assertEquals("10.00", coins(ALICE), "a retried mint must not credit the player twice");
        assertEquals(1L, transactionCount(), "exactly one mint transaction must exist");
    }

    @Test
    void sameKeyWithAnotherBodyAnswers409IdempotencyConflict() {
        post("mint-conflict", mint(ALICE, "10.00"));

        HttpClientResponseException problem = assertThrows(HttpClientResponseException.class,
                () -> post("mint-conflict", mint(ALICE, "11.00")),
                "a key reused with another body must be refused");

        assertProblem(problem, HttpStatus.CONFLICT, ProblemCode.IDEMPOTENCY_CONFLICT);
        assertEquals("10.00", coins(ALICE), "the refused reuse must not book the second body");
    }

    @Test
    void seventeenMetadataEntriesAnswer422MetadataTooLarge() {
        HttpClientResponseException problem = assertThrows(HttpClientResponseException.class,
                () -> post("metadata-too-large", transferWithMetadata(ALICE, BOB, "1.00", 17)),
                "17 metadata entries must be refused");

        assertProblem(problem, HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.METADATA_TOO_LARGE);
    }

    private HttpResponse<TransactionResponse> post(String key, String body) {
        return postAs(TOKENS.client(), key, body);
    }

    private HttpResponse<TransactionResponse> postAs(String token, String key, String body) {
        return client.toBlocking().exchange(request(token, key, body), TransactionResponse.class);
    }

    private static MutableHttpRequest<String> request(String token, String key, String body) {
        MutableHttpRequest<String> request = HttpRequest.POST("/v1/transactions", body)
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .bearerAuth(token);
        return key == null ? request : request.header("Idempotency-Key", key);
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

    private static String mint(UUID player, String amount) {
        return "{\"kind\":\"MINT\",\"to\":{\"player\":\"" + player + "\"},\"amount\":" + amount(amount) + "}";
    }

    private static String burn(UUID player, String amount) {
        return "{\"kind\":\"BURN\",\"from\":{\"player\":\"" + player + "\"},\"amount\":" + amount(amount) + "}";
    }

    /** A transfer between two players, named by {@code {"player": uuid}}, acted for by the payer. */
    private static String transfer(UUID from, UUID to, String amount) {
        return transferJson("{\"player\":\"" + from + "\"}", "{\"player\":\"" + to + "\"}", amount,
                ",\"actor\":\"" + from + "\"");
    }

    /** A transfer whose payer is named by an account id, not by player. */
    private static String transferFromAccount(UUID fromAccount, UUID to, String amount) {
        return transferJson("\"" + fromAccount + "\"", "{\"player\":\"" + to + "\"}", amount, "");
    }

    private static String transferWithMetadata(UUID from, UUID to, String amount, int entries) {
        StringBuilder metadata = new StringBuilder("{");
        for (int i = 0; i < entries; i++) {
            metadata.append(i == 0 ? "" : ",").append("\"k").append(i).append("\":\"v\"");
        }
        metadata.append("}");
        return transferJson("{\"player\":\"" + from + "\"}", "{\"player\":\"" + to + "\"}", amount,
                ",\"actor\":\"" + from + "\",\"metadata\":" + metadata);
    }

    private static String transferJson(String from, String to, String amount, String extraFields) {
        return "{\"kind\":\"TRANSFER\",\"from\":" + from + ",\"to\":" + to + ",\"amount\":" + amount(amount)
                + extraFields + "}";
    }

    private static String batch(String... postings) {
        return "{\"kind\":\"BATCH\",\"postings\":[" + String.join(",", postings) + "]}";
    }

    private static String posting(UUID player, String direction, String amount) {
        return "{\"account\":{\"player\":\"" + player + "\"},\"direction\":\"" + direction + "\",\"amount\":"
                + amount(amount) + "}";
    }

    private static String amount(String value) {
        return "{\"amount\":\"" + value + "\",\"currency\":\"COINS\"}";
    }
}
