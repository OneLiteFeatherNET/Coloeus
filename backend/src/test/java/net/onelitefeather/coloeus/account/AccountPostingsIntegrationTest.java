package net.onelitefeather.coloeus.account;

import static net.onelitefeather.coloeus.support.ProblemAssertions.assertProblem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.onelitefeather.coloeus.auth.Permission;
import net.onelitefeather.coloeus.currency.CreateCurrencyRequest;
import net.onelitefeather.coloeus.currency.CurrencyService;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.support.LedgerApiIntegrationTest;
import net.onelitefeather.coloeus.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@Tag("integration")
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountPostingsIntegrationTest extends LedgerApiIntegrationTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Inject
    CurrencyService currencies;

    @BeforeEach
    void defineCoinsAndRegisterTheClients() {
        currencies.create(new CreateCurrencyRequest("COINS", "Coin", "Coins", "C", 2));
        registerClient(TestTokens.CLIENT_APP);
        grant(TestTokens.CLIENT_APP, "COINS", Permission.MINT);
        grant(TestTokens.CLIENT_APP, "COINS", Permission.READ);
        registerClient(TestTokens.ADMIN_APP);
    }

    @Test
    void postingsAreNewestFirstWithCounterAccountKindReasonAndActor() {
        mint(BOB, "10.00", "mint-bob");
        clock.advance(Duration.ofMinutes(1));
        // The admin books the transfer: the actor "steve" is not the payer, which only an admin may do.
        post(TOKENS.admin(), "transfer-bob", "{\"kind\":\"TRANSFER\",\"from\":{\"player\":\"" + BOB + "\"},"
                + "\"to\":{\"player\":\"" + ALICE + "\"},\"amount\":" + amount("3.00")
                + ",\"reason\":\"shop\",\"actor\":\"steve\"}");

        PostingsResponse page = postings(BOB, "");

        PostingsResponse.PostingView newest = page.items().get(0);
        assertEquals("TRANSFER", newest.kind(), "the newest posting must be the transfer");
        assertEquals("-3.00", newest.amount().amount(), "a payer's posting must be a debit");
        assertEquals("shop", newest.reason(), "the reason of the transaction must be returned");
        assertEquals("steve", newest.actor(), "the actor of the transaction must be returned");
        assertEquals(List.of(playerAccount(ALICE)), newest.counterAccounts(),
                "the counter account must be the payee's account");
        PostingsResponse.PostingView older = page.items().get(1);
        assertEquals("MINT", older.kind(), "the older posting must be the mint");
        assertEquals("10.00", older.amount().amount(), "the mint must credit the player");
    }

    @Test
    void cursorContinuesExactlyAfterTheLastPostingWithoutDuplicates() {
        for (int i = 0; i < 30; i++) {
            mint(ALICE, "1.00", "mint-" + i);
        }

        PostingsResponse first = postings(ALICE, "?limit=20");
        PostingsResponse second = postings(ALICE, "?limit=20&cursor=" + first.nextCursor());

        assertEquals(20, first.items().size(), "the first page must hold the requested 20 postings");
        assertNotEquals(null, first.nextCursor(), "a page that is not the last one must carry a cursor");
        assertEquals(10, second.items().size(), "the second page must hold the remaining 10 postings");
        assertNull(second.nextCursor(), "the last page must not carry a cursor");
        List<UUID> ids = new ArrayList<>();
        first.items().forEach(item -> ids.add(item.id()));
        second.items().forEach(item -> ids.add(item.id()));
        assertEquals(30, new HashSet<>(ids).size(), "the pages must not repeat a posting");
    }

    @Test
    void withoutALimitTheHistoryUpToTheDefaultPageIsReturnedInOnePage() {
        for (int i = 0; i < 30; i++) {
            mint(ALICE, "1.00", "mint-" + i);
        }

        PostingsResponse page = postings(ALICE, "");

        assertEquals(30, page.items().size(), "30 postings fit into the default page of 50");
        assertNull(page.nextCursor(), "a complete history must not carry a cursor");
    }

    @Test
    void limitAboveTheMaximumIsRejected() {
        HttpClientResponseException problem = assertThrows(HttpClientResponseException.class,
                () -> postings(ALICE, "?limit=201"),
                "a page above 200 postings must be refused");

        assertProblem(problem, HttpStatus.BAD_REQUEST, ProblemCode.REQUEST_INVALID);
    }

    @Test
    void malformedCursorIsRejected() {
        HttpClientResponseException problem = assertThrows(HttpClientResponseException.class,
                () -> postings(ALICE, "?cursor=not-a-cursor!"),
                "a cursor that was not issued by the service must be refused");

        assertProblem(problem, HttpStatus.BAD_REQUEST, ProblemCode.REQUEST_INVALID);
    }

    @Test
    void postingsOfAnUnknownAccountAnswer404() {
        HttpClientResponseException problem = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(get(TOKENS.client(), "/v1/accounts/" + UUID.randomUUID() + "/postings")),
                "an unknown account has no postings to read");

        assertProblem(problem, HttpStatus.NOT_FOUND, ProblemCode.ACCOUNT_NOT_FOUND);
    }

    /** The postings of a player's account; the id of the account is resolved from the player UUID first. */
    private PostingsResponse postings(UUID player, String query) {
        return client.toBlocking().retrieve(
                get(TOKENS.client(), "/v1/accounts/" + playerAccount(player) + "/postings" + query),
                PostingsResponse.class);
    }

    private UUID playerAccount(UUID player) {
        return client.toBlocking().retrieve(get(TOKENS.client(), "/v1/accounts/player/" + player),
                PlayerAccountResponse.class).id();
    }

    private void mint(UUID player, String value, String key) {
        post(TOKENS.client(), key, "{\"kind\":\"MINT\",\"to\":{\"player\":\"" + player + "\"},\"amount\":"
                + amount(value) + "}");
    }

    private void post(String token, String key, String body) {
        client.toBlocking().exchange(HttpRequest.POST("/v1/transactions", body)
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .bearerAuth(token)
                .header("Idempotency-Key", key));
    }

    private static String amount(String value) {
        return "{\"amount\":\"" + value + "\",\"currency\":\"COINS\"}";
    }
}
