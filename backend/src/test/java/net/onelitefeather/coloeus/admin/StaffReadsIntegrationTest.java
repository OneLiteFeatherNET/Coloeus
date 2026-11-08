package net.onelitefeather.coloeus.admin;

import static net.onelitefeather.coloeus.support.ProblemAssertions.assertProblem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.money.MonetaryAmount;
import net.onelitefeather.coloeus.account.AccountKind;
import net.onelitefeather.coloeus.auth.Caller;
import net.onelitefeather.coloeus.auth.Permission;
import net.onelitefeather.coloeus.currency.CreateCurrencyRequest;
import net.onelitefeather.coloeus.currency.CurrencyService;
import net.onelitefeather.coloeus.ledger.AccountRef;
import net.onelitefeather.coloeus.ledger.BookingService;
import net.onelitefeather.coloeus.ledger.Leg;
import net.onelitefeather.coloeus.ledger.TransactionCommand;
import net.onelitefeather.coloeus.ledger.TransactionKind;
import net.onelitefeather.coloeus.persistence.JdbcTransactions;
import net.onelitefeather.coloeus.player.PlayerNameService;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.support.Api;
import net.onelitefeather.coloeus.support.LedgerApiIntegrationTest;
import net.onelitefeather.coloeus.support.TestClockFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.javamoney.moneta.Money;

/**
 * The read endpoints of the staff API (staff-api and staff-money-flow specs), through the HTTP stack on a real database.
 * Every test writes its own fixtures from the injected clock, so the figures are fixed.
 */
@Tag("integration")
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StaffReadsIntegrationTest extends LedgerApiIntegrationTest {

    private static final UUID APP = UUID.fromString("00000000-0000-4000-8000-0000000000c2");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-4000-8000-0000000000b2");

    @Inject
    CurrencyService currencies;

    @Inject
    BookingService booking;

    @Inject
    JdbcTransactions transactions;

    @Inject
    PlayerNameService names;

    @Inject
    JsonMapper json;

    @BeforeEach
    void fixtures() throws Exception {
        clock.set(TestClockFactory.START);
        currencies.create(new CreateCurrencyRequest("COINS", "Coin", "Coins", "C", 2));
        registerClient(APP);
        grant(APP, "COINS", Permission.MINT);
        grant(APP, "COINS", Permission.BURN);
    }

    @Test
    void nameSearchListsTheNewestHolderFirst() throws Exception {
        names.recordJoin(PLAYER.toString(), "Steve");
        clock.advance(Duration.ofDays(1));
        names.recordJoin(OTHER.toString(), "steve");

        JsonNode items = get("/v1/staff/accounts/search?name=STEVE").get("items");

        assertEquals(OTHER.toString(), items.get(0).get("uuid").getStringValue(),
                "the UUID that held the name most recently must come first");
        assertEquals(2, items.size(), "both holders of the name must be listed");
    }

    @Test
    void nameSearchOfAnUnknownNameIsEmpty() throws Exception {
        JsonNode items = get("/v1/staff/accounts/search?name=Nobody").get("items");

        assertEquals(0, items.size(), "an unknown name must give an empty list, without a Mojang request");
    }

    @Test
    void searchWithBothParametersIsRefused() {
        HttpClientResponseException refusal = Api.refusal(client, Api.bearer(HttpRequest.GET(
                "/v1/staff/accounts/search?name=Steve&uuid=" + PLAYER), TOKENS.staffModerator()));

        assertProblem(refusal, HttpStatus.BAD_REQUEST, ProblemCode.REQUEST_INVALID);
    }

    @Test
    void uuidWithoutAccountIsReportedWithNullAccount() throws Exception {
        JsonNode hit = get("/v1/staff/accounts/search?uuid=" + OTHER).get("items").get(0);

        assertTrue(hit.get("accountId").isNull(), "a player without an account must have a null accountId");
    }

    @Test
    void accountDetailShowsEveryBalanceAsDecimalString() throws Exception {
        mint(PLAYER, "1250", clock.instant());
        UUID account = playerAccount(PLAYER);

        JsonNode detail = get("/v1/staff/accounts/" + account);

        assertEquals("12.50", detail.get("balances").get(0).get("amount").getStringValue(),
                "the balance must keep the currency's two fraction digits");
        assertEquals("COINS", detail.get("balances").get(0).get("currency").getStringValue());
    }

    @Test
    void unknownAccountIsNotFound() {
        HttpClientResponseException refusal = Api.refusal(client, Api.bearer(
                HttpRequest.GET("/v1/staff/accounts/" + OTHER), TOKENS.staffModerator()));

        assertProblem(refusal, HttpStatus.NOT_FOUND, ProblemCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    void secondPageOfPostingsHoldsTheRemainingPosting() throws Exception {
        mint(PLAYER, "100", clock.instant());
        clock.advance(Duration.ofMinutes(1));
        mint(PLAYER, "200", clock.instant());
        clock.advance(Duration.ofMinutes(1));
        mint(PLAYER, "300", clock.instant());
        UUID account = playerAccount(PLAYER);

        JsonNode first = get("/v1/staff/accounts/" + account + "/postings?limit=2");
        JsonNode second = get("/v1/staff/accounts/" + account + "/postings?limit=2&cursor="
                + first.get("nextCursor").getStringValue());

        assertEquals(2, first.get("items").size(), "the first page must hold the limit");
        assertEquals(1, second.get("items").size(), "the second page must hold the remaining posting");
        assertTrue(second.get("nextCursor").isNull(), "the last page must have no cursor");
    }

    @Test
    void sevenDayWindowLeavesOutOlderMints() throws Exception {
        clock.set(TestClockFactory.START.minus(Duration.ofDays(8)));
        mint(PLAYER, "10000", clock.instant());
        clock.set(TestClockFactory.START);
        mint(PLAYER, "2000", clock.instant());

        JsonNode coins = currencyFlow(get("/v1/staff/money-flow"), "COINS");

        assertEquals("20", coins.get("created").getStringValue(), "the 7 day window must count only the recent mint");
    }

    @Test
    void allWindowCountsEveryMint() throws Exception {
        clock.set(TestClockFactory.START.minus(Duration.ofDays(8)));
        mint(PLAYER, "10000", clock.instant());
        clock.set(TestClockFactory.START);
        mint(PLAYER, "2000", clock.instant());

        JsonNode coins = currencyFlow(get("/v1/staff/money-flow?window=ALL"), "COINS");

        assertEquals("120", coins.get("created").getStringValue(), "the all-time window must count both mints");
        assertTrue(get("/v1/staff/money-flow?window=ALL").get("from").isNull(), "ALL has no start instant");
    }

    @Test
    void circulationIsTheSumOfPlayerBalancesWhateverTheWindow() throws Exception {
        clock.set(TestClockFactory.START.minus(Duration.ofDays(8)));
        mint(PLAYER, "4000", clock.instant());
        clock.set(TestClockFactory.START);

        JsonNode seven = currencyFlow(get("/v1/staff/money-flow"), "COINS");
        JsonNode all = currencyFlow(get("/v1/staff/money-flow?window=ALL"), "COINS");

        assertEquals("40", seven.get("circulation").getStringValue());
        assertEquals("40", all.get("circulation").getStringValue());
    }

    @Test
    void dailySeriesCountsAMintOfTheLateUtcEveningOnItsDay() throws Exception {
        clock.set(Instant.parse("2026-11-07T23:30:00Z"));
        mint(PLAYER, "500", clock.instant());
        clock.set(Instant.parse("2026-11-08T10:00:00Z"));

        JsonNode days = get("/v1/staff/money-flow/series?currency=COINS&days=2").get("days");

        assertEquals("2026-11-07", days.get(0).get("date").getStringValue());
        assertEquals("5", days.get(0).get("created").getStringValue(), "the mint belongs to 7 November in UTC");
        assertEquals("0", days.get(1).get("created").getStringValue(), "8 November has no mint yet");
    }

    @Test
    void seriesForAnUnknownCurrencyIsNotFound() {
        HttpClientResponseException refusal = Api.refusal(client, Api.bearer(
                HttpRequest.GET("/v1/staff/money-flow/series?currency=NOPE"), TOKENS.staffModerator()));

        assertProblem(refusal, HttpStatus.NOT_FOUND, ProblemCode.CURRENCY_NOT_FOUND);
    }

    @Test
    void unknownWindowIsRefused() {
        HttpClientResponseException refusal = Api.refusal(client, Api.bearer(
                HttpRequest.GET("/v1/staff/money-flow?window=30D"), TOKENS.staffModerator()));

        assertProblem(refusal, HttpStatus.BAD_REQUEST, ProblemCode.REQUEST_INVALID);
    }

    @Test
    void moneyFlowCreatesNoAccountForAClientWithoutTraffic() throws Exception {
        registerClient(OTHER);

        get("/v1/staff/money-flow");

        assertEquals(0, sourceAccounts(OTHER), "reading the flow must not create a SOURCE account");
    }

    @Test
    void theMoneyFlowHasNoAmwMember() throws Exception {
        String body = raw("/v1/staff/money-flow");

        assertFalse(body.contains("\"amw\""), "AMW is not computed by this change");
    }

    @Test
    void staffActionLogIsEmptyWithoutWrites() throws Exception {
        assertEquals(0, get("/v1/staff/staff-actions").get("items").size());
    }

    @Test
    void clientsAreListedWithTheirGrants() throws Exception {
        JsonNode client = get("/v1/staff/clients").get("items").get(0);

        assertEquals(APP.toString(), client.get("clientId").getStringValue());
        assertEquals(2, client.get("grants").size(), "the MINT and BURN grants must be listed");
    }

    @Test
    void aCallWithoutTokenIsRefused() {
        HttpClientResponseException refusal = Api.refusal(client, HttpRequest.GET("/v1/staff/clients"));

        assertProblem(refusal, HttpStatus.UNAUTHORIZED, ProblemCode.UNAUTHORIZED);
    }

    // ---- helpers ----

    private JsonNode get(String path) throws Exception {
        return json.readValue(raw(path), JsonNode.class);
    }

    private String raw(String path) throws Exception {
        return raw(Api.bearer(HttpRequest.GET(path), TOKENS.staffModerator()));
    }

    private String raw(io.micronaut.http.MutableHttpRequest<?> request) {
        return client.toBlocking().retrieve(request);
    }

    private static JsonNode currencyFlow(JsonNode flow, String code) {
        for (JsonNode currency : flow.get("currencies").values()) {
            if (currency.get("currency").getStringValue().equals(code)) {
                return currency;
            }
        }
        throw new AssertionError("no flow for " + code);
    }

    /** Mints {@code amountMinor} hundredths of COINS to a player through the booking service. */
    private void mint(UUID player, String amountMinor, Instant when) {
        MonetaryAmount amount = Money.of(new java.math.BigDecimal(amountMinor).movePointLeft(2),
                net.onelitefeather.coloeus.money.ColoeusCurrencyProvider.find("COINS").orElseThrow());
        TransactionCommand command = new TransactionCommand(TransactionKind.MINT, List.of(
                new Leg(new AccountRef.ClientSystem(AccountKind.SOURCE), amount.negate()),
                new Leg(new AccountRef.Player(player), amount)),
                "fixture", null, null, Map.of());
        Caller caller = new Caller(APP, "fixture", Set.of(), false);
        transactions.inTransaction(connection -> booking.book(connection, caller, command, when, "{}"));
    }

    private UUID playerAccount(UUID player) throws Exception {
        try (var connection = connection();
             var statement = connection.prepareStatement("SELECT id FROM account WHERE owner_uuid = ?")) {
            statement.setObject(1, player);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getObject(1, UUID.class);
            }
        }
    }

    private int sourceAccounts(UUID clientId) throws Exception {
        try (var connection = connection();
             var statement = connection.prepareStatement("SELECT count(*) FROM account WHERE client_id = ?")) {
            statement.setObject(1, clientId);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }
}
