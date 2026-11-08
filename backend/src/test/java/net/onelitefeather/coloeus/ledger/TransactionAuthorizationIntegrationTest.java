package net.onelitefeather.coloeus.ledger;

import static net.onelitefeather.coloeus.support.Api.assertProblem;
import static net.onelitefeather.coloeus.support.Api.refusal;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import net.onelitefeather.coloeus.account.PlayerAccountResponse;
import net.onelitefeather.coloeus.auth.Permission;
import net.onelitefeather.coloeus.currency.CreateCurrencyRequest;
import net.onelitefeather.coloeus.currency.CurrencyService;
import net.onelitefeather.coloeus.money.AmountJson;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.support.LedgerApiIntegrationTest;
import net.onelitefeather.coloeus.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Who may book what on {@code POST /v1/transactions} and read accounts: the grants per currency, the actor rule for
 * transfers from player accounts, the admin role for system accounts, and the registration of the caller.
 */
@Tag("integration")
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransactionAuthorizationIntegrationTest extends LedgerApiIntegrationTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID UNREGISTERED_APP = UUID.fromString("00000000-0000-4000-8000-0000000000c2");

    @Inject
    CurrencyService currencies;

    @BeforeEach
    void defineCoins() {
        currencies.create(new CreateCurrencyRequest("COINS", "Coin", "Coins", "C", 2));
    }

    @Test
    void mintWithoutTheMintGrantAnswers403AndBooksNothing() {
        register(TestTokens.CLIENT_APP, Permission.BURN, Permission.READ);

        HttpClientResponseException failure = refusal(client,
                post(TOKENS.client(), "mint-without-grant", mint(ALICE, "5.00")));

        assertProblem(failure, HttpStatus.FORBIDDEN, ProblemCode.FORBIDDEN_GRANT);
        assertEquals(0L, transactionCount(), "a refused mint must store no transaction");
    }

    @Test
    void burnWithoutTheBurnGrantAnswers403AndKeepsTheBalance() {
        register(TestTokens.CLIENT_APP, Permission.MINT, Permission.READ);
        fund(ALICE, "10.00");

        HttpClientResponseException failure = refusal(client,
                post(TOKENS.client(), "burn-without-grant", burn(ALICE, "4.00")));

        assertProblem(failure, HttpStatus.FORBIDDEN, ProblemCode.FORBIDDEN_GRANT);
        assertEquals("10.00", coins(ALICE), "a refused burn must leave the balance unchanged");
    }

    @Test
    void transferOnBehalfWithAnotherActorAnswers403ActorMismatch() {
        register(TestTokens.CLIENT_APP, Permission.MINT, Permission.TRANSFER_ON_BEHALF, Permission.READ);
        fund(ALICE, "10.00");

        HttpClientResponseException failure = refusal(client,
                post(TOKENS.client(), "transfer-wrong-actor", transfer(ALICE, BOB, "4.00", BOB)));

        assertProblem(failure, HttpStatus.FORBIDDEN, ProblemCode.ACTOR_MISMATCH);
        assertEquals("10.00", coins(ALICE), "a refused transfer must not debit the payer");
        assertEquals("0.00", coins(BOB), "a refused transfer must not credit the payee");
    }

    @Test
    void transferOnBehalfWithThePayerAsActorAnswers201() {
        register(TestTokens.CLIENT_APP, Permission.MINT, Permission.TRANSFER_ON_BEHALF, Permission.READ);
        fund(ALICE, "10.00");

        var response = client.toBlocking().exchange(
                post(TOKENS.client(), "transfer-right-actor", transfer(ALICE, BOB, "4.00", ALICE)),
                TransactionResponse.class);

        assertEquals(HttpStatus.CREATED, response.status(), "a transfer acted for by its payer must be booked");
        assertEquals("6.00", coins(ALICE), "the payer must be debited");
        assertEquals("4.00", coins(BOB), "the payee must be credited");
    }

    @Test
    void transferFromAPlayerWithoutTheTransferGrantAnswers403ForbiddenGrant() {
        register(TestTokens.CLIENT_APP, Permission.MINT, Permission.READ);
        fund(ALICE, "10.00");

        HttpClientResponseException failure = refusal(client,
                post(TOKENS.client(), "transfer-no-grant", transfer(ALICE, BOB, "4.00", ALICE)));

        assertProblem(failure, HttpStatus.FORBIDDEN, ProblemCode.FORBIDDEN_GRANT);
        assertEquals("10.00", coins(ALICE), "a refused transfer must not debit the payer");
    }

    @Test
    void adminTransfersForAnyPlayerWithoutTheTransferGrantAnswers201() {
        register(TestTokens.CLIENT_APP, Permission.MINT, Permission.READ);
        register(TestTokens.ADMIN_APP);
        fund(ALICE, "10.00");

        var response = client.toBlocking().exchange(
                post(TOKENS.admin(), "admin-transfer", transfer(ALICE, BOB, "4.00", null)),
                TransactionResponse.class);

        assertEquals(HttpStatus.CREATED, response.status(), "an admin may debit any player without the grant");
        assertEquals("6.00", coins(ALICE), "the payer must be debited");
        assertEquals("4.00", coins(BOB), "the payee must be credited");
    }

    @Test
    void unregisteredAdminCannotWrite403ClientNotRegistered() {
        HttpClientResponseException failure = refusal(client,
                post(TOKENS.admin(), "admin-unregistered", mint(ALICE, "5.00")));

        assertProblem(failure, HttpStatus.FORBIDDEN, ProblemCode.CLIENT_NOT_REGISTERED);
        assertEquals(0L, transactionCount(), "an unregistered admin must store no transaction");
    }

    @Test
    void aStaffTokenOfTheWebClientCannotWriteOnTheLedger() {
        HttpClientResponseException failure = refusal(client,
                post(TOKENS.staffAdmin(), "staff-on-ledger", mint(ALICE, "5.00")));

        assertProblem(failure, HttpStatus.FORBIDDEN, ProblemCode.CLIENT_NOT_REGISTERED);
        assertEquals(0L, transactionCount(), "the web client is never a ledger client, so nothing is stored");
    }

    @Test
    void unregisteredClientPostAnswers403ClientNotRegistered() {
        HttpClientResponseException failure = refusal(client,
                post(TOKENS.clientOf(UNREGISTERED_APP), "unregistered-post", mint(ALICE, "5.00")));

        assertProblem(failure, HttpStatus.FORBIDDEN, ProblemCode.CLIENT_NOT_REGISTERED);
        assertEquals(0L, transactionCount(), "an unregistered client must store no transaction");
    }

    @Test
    void moderatorCannotPostAnswers403ForbiddenRole() {
        register(TestTokens.MODERATOR_APP, Permission.MINT, Permission.TRANSFER_ON_BEHALF, Permission.READ);

        HttpClientResponseException failure = refusal(client,
                post(TOKENS.moderator(), "moderator-post", mint(ALICE, "5.00")));

        assertProblem(failure, HttpStatus.FORBIDDEN, ProblemCode.FORBIDDEN_ROLE);
        assertEquals(0L, transactionCount(), "a moderator must store no transaction, even with grants");
    }

    @Test
    void debitingTheFeeAccountNeedsTheAdminRole() {
        register(TestTokens.CLIENT_APP, Permission.TRANSFER_ON_BEHALF, Permission.READ);

        HttpClientResponseException failure = refusal(client,
                post(TOKENS.client(), "debit-fee", "{\"kind\":\"TRANSFER\","
                        + "\"from\":{\"system\":\"FEE\",\"currency\":\"COINS\"},"
                        + "\"to\":{\"player\":\"" + BOB + "\"},\"amount\":" + amount("1.00") + "}"));

        assertProblem(failure, HttpStatus.FORBIDDEN, ProblemCode.FORBIDDEN_ROLE);
        assertEquals(0L, transactionCount(), "a refused debit of the fee account must store no transaction");
    }

    @Test
    void registeredClientWithoutAReadGrantCannotReadAnAccount403() {
        register(TestTokens.CLIENT_APP, Permission.MINT);

        HttpClientResponseException failure = refusal(client,
                get(TOKENS.client(), "/v1/accounts/player/" + ALICE));

        assertProblem(failure, HttpStatus.FORBIDDEN, ProblemCode.FORBIDDEN_GRANT);
    }

    @Test
    void moderatorMayReadAnAccountWithoutAGrant() {
        var response = client.toBlocking().exchange(
                get(TOKENS.moderator(), "/v1/accounts/player/" + ALICE), PlayerAccountResponse.class);

        assertEquals(HttpStatus.OK, response.status(), "a moderator may read accounts");
    }

    /** Registers a client by its application id with the given grants on COINS. */
    private static void register(UUID app, Permission... permissions) {
        registerClient(app);
        for (Permission permission : permissions) {
            grant(app, "COINS", permission);
        }
    }

    /** Credits a player with a mint of the registered client that holds the MINT grant. */
    private void fund(UUID player, String amount) {
        var response = client.toBlocking().exchange(
                post(TOKENS.client(), "fund-" + player, mint(player, amount)), TransactionResponse.class);
        assertEquals(HttpStatus.CREATED, response.status(), "funding the test player must succeed");
    }

    private static MutableHttpRequest<String> post(String token, String key, String body) {
        return HttpRequest.POST("/v1/transactions", body)
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .bearerAuth(token)
                .header("Idempotency-Key", key);
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

    /** A transfer between two players; {@code actor} is omitted when null. */
    private static String transfer(UUID from, UUID to, String amount, UUID actor) {
        return "{\"kind\":\"TRANSFER\",\"from\":{\"player\":\"" + from + "\"},\"to\":{\"player\":\"" + to + "\"},"
                + (actor == null ? "" : "\"actor\":\"" + actor + "\",")
                + "\"amount\":" + amount(amount) + "}";
    }

    private static String amount(String value) {
        return "{\"amount\":\"" + value + "\",\"currency\":\"COINS\"}";
    }
}
