package net.onelitefeather.coloeus.admin;

import static net.onelitefeather.coloeus.support.ProblemAssertions.assertProblem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.money.MonetaryAmount;
import net.onelitefeather.coloeus.account.AccountKind;
import net.onelitefeather.coloeus.auth.Caller;
import net.onelitefeather.coloeus.auth.Permission;
import net.onelitefeather.coloeus.auth.Roles;
import net.onelitefeather.coloeus.currency.CreateCurrencyRequest;
import net.onelitefeather.coloeus.currency.CurrencyService;
import net.onelitefeather.coloeus.ledger.AccountRef;
import net.onelitefeather.coloeus.ledger.BookingService;
import net.onelitefeather.coloeus.ledger.Leg;
import net.onelitefeather.coloeus.ledger.TransactionCommand;
import net.onelitefeather.coloeus.ledger.TransactionKind;
import net.onelitefeather.coloeus.money.ColoeusCurrencyProvider;
import net.onelitefeather.coloeus.persistence.JdbcTransactions;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.support.Api;
import net.onelitefeather.coloeus.support.LedgerApiIntegrationTest;
import net.onelitefeather.coloeus.support.TestClockFactory;
import net.onelitefeather.coloeus.support.TestTokens;
import org.javamoney.moneta.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * The write endpoints of the staff API (staff-operations and staff-api specs): reset and adjust, idempotency, the
 * audit rows, and the configuration writes. Each test starts from the same fixture: one player with 12.50 COINS.
 */
@Tag("integration")
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StaffWritesIntegrationTest extends LedgerApiIntegrationTest {

    private static final UUID APP = UUID.fromString("00000000-0000-4000-8000-0000000000c3");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-4000-8000-0000000000a2");

    @Inject
    CurrencyService currencies;

    @Inject
    BookingService booking;

    @Inject
    JdbcTransactions transactions;

    @Inject
    JsonMapper json;

    @BeforeEach
    void fixture() throws Exception {
        clock.set(TestClockFactory.START);
        currencies.create(new CreateCurrencyRequest("COINS", "Coin", "Coins", "C", 2));
        currencies.create(new CreateCurrencyRequest("GEMS", "Gem", "Gems", "G", 0));
        registerClient(APP);
        grant(APP, "COINS", Permission.MINT);
        mint(PLAYER, "1250");
    }

    @Test
    void adminResetBooksTheWholeBalanceOfTheCurrency() throws Exception {
        String admin = TOKENS.staffAdmin();

        JsonNode answer = json.readValue(post(admin, "/v1/staff/accounts/" + account() + "/reset",
                "{\"currency\": \"COINS\", \"reason\": \"Dupe fixed\"}", "reset-1").getBody(String.class).orElseThrow(),
                JsonNode.class);

        assertEquals("RESET", answer.get("kind").getStringValue());
        assertEquals("-12.50", answer.get("movements").get(0).get("amount").getStringValue(),
                "the movement must be the player's negative balance");
        assertEquals("0.00", balance(PLAYER, "COINS"), "the player must hold nothing afterwards");
    }

    @Test
    void resetOfAnEmptyAccountIsNothingToReset() throws Exception {
        HttpClientResponseException refusal = Api.refusal(client, write(TOKENS.staffAdmin(),
                "/v1/staff/accounts/" + account() + "/reset", "{\"currency\": \"GEMS\", \"reason\": \"Check\"}",
                "reset-2"));

        assertProblem(refusal, HttpStatus.CONFLICT, ProblemCode.NOTHING_TO_RESET);
        assertEquals(0, staffActions("RESET"), "a refused reset must write no staff action");
    }

    @Test
    void resetBooksAStaffActionWithItsTransaction() throws Exception {
        post(TOKENS.staffAdmin(), "/v1/staff/accounts/" + account() + "/reset",
                "{\"currency\": null, \"reason\": \"Ban cleanup\"}", "reset-3");

        assertEquals(1, staffActions("RESET"), "one RESET row must exist");
        assertEquals(1, staffActionsWithTransaction(), "the RESET row must name its journal transaction");
    }

    @Test
    void resetTransactionIsAttributedToTheStaffMember() throws Exception {
        post(TOKENS.staffAdmin(), "/v1/staff/accounts/" + account() + "/reset",
                "{\"currency\": \"COINS\", \"reason\": \"Dupe fixed\"}", "reset-4");

        assertEquals(TestTokens.STAFF_ADMIN_OID, scalar("SELECT actor FROM transaction WHERE kind = 'RESET'"),
                "the actor must be the oid of the staff member");
        assertEquals("Alex Admin", scalar("SELECT metadata ->> 'staff_name' FROM transaction WHERE kind = 'RESET'"),
                "the metadata must carry the staff display name");
        assertEquals(null, scalar("SELECT client_id::text FROM transaction WHERE kind = 'RESET'"),
                "a staff transaction has no client");
    }

    @Test
    void resetBooksNoClientAccount() throws Exception {
        post(TOKENS.staffAdmin(), "/v1/staff/accounts/" + account() + "/reset",
                "{\"currency\": \"COINS\", \"reason\": \"Dupe fixed\"}", "reset-5");

        assertEquals("0", scalar("SELECT count(*)::text FROM posting p JOIN account a ON a.id = p.account_id "
                + "JOIN transaction t ON t.id = p.transaction_id WHERE t.kind = 'RESET' "
                + "AND a.kind IN ('SOURCE', 'SINK')"), "a staff write must not touch SOURCE or SINK accounts");
    }

    @Test
    void moderatorResetIsRefusedWithForbiddenRole() {
        HttpClientResponseException refusal = Api.refusal(client, write(TOKENS.staffModerator(),
                "/v1/staff/accounts/" + account() + "/reset", "{\"currency\": \"COINS\", \"reason\": \"x\"}", "reset-6"));

        assertProblem(refusal, HttpStatus.FORBIDDEN, ProblemCode.FORBIDDEN_ROLE);
    }

    @Test
    void creditBooksTheAdminSourceAndTheAmountToThePlayer() throws Exception {
        JsonNode answer = json.readValue(post(TOKENS.staffAdmin(), "/v1/staff/accounts/" + account() + "/adjust",
                "{\"currency\": \"COINS\", \"direction\": \"CREDIT\", \"amount\": \"10.00\", \"reason\": \"Event prize\"}",
                "adjust-1").getBody(String.class).orElseThrow(), JsonNode.class);

        assertEquals("ADJUST", answer.get("kind").getStringValue());
        assertEquals("10.00", answer.get("movements").get(0).get("amount").getStringValue());
        assertEquals("22.50", balance(PLAYER, "COINS"), "the player must have 10.00 more");
        assertEquals("ADJUST", scalar("SELECT kind FROM transaction WHERE reason = 'Event prize'"));
    }

    @Test
    void debitBeyondTheBalanceIsInsufficientFunds() throws Exception {
        HttpClientResponseException refusal = Api.refusal(client, write(TOKENS.staffAdmin(),
                "/v1/staff/accounts/" + account() + "/adjust",
                "{\"currency\": \"COINS\", \"direction\": \"DEBIT\", \"amount\": \"13.00\", \"reason\": \"Too much\"}",
                "adjust-2"));

        assertProblem(refusal, HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.INSUFFICIENT_FUNDS);
        assertEquals("12.50", balance(PLAYER, "COINS"), "a refused debit must leave the balance unchanged");
        assertEquals(0, staffActions("ADJUST"), "a refused adjust must write no staff action");
    }

    @Test
    void amountWithTooManyFractionDigitsIsScaleExceeded() {
        HttpClientResponseException refusal = Api.refusal(client, write(TOKENS.staffAdmin(),
                "/v1/staff/accounts/" + account() + "/adjust",
                "{\"currency\": \"COINS\", \"direction\": \"CREDIT\", \"amount\": \"12.505\", \"reason\": \"Rounding\"}",
                "adjust-3"));

        assertProblem(refusal, HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.SCALE_EXCEEDED);
    }

    @Test
    void missingIdempotencyKeyIsRefused() {
        HttpClientResponseException refusal = Api.refusal(client, writeWith(
                HttpRequest.POST("/v1/staff/clients", "{\"clientId\": \"" + UUID.randomUUID()
                        + "\", \"displayName\": \"lobby\"}"), TOKENS.staffAdmin(), null));

        assertProblem(refusal, HttpStatus.BAD_REQUEST, ProblemCode.IDEMPOTENCY_KEY_MISSING);
    }

    @Test
    void sameKeyAndBodyReplaysTheStoredAnswerWithoutASecondTransaction() throws Exception {
        String admin = TOKENS.staffAdmin();
        String body = "{\"currency\": \"COINS\", \"direction\": \"CREDIT\", \"amount\": \"5.00\", \"reason\": \"Retry\"}";
        String first = post(admin, "/v1/staff/accounts/" + account() + "/adjust", body, "retry-1")
                .getBody(String.class).orElseThrow();

        String replay = post(admin, "/v1/staff/accounts/" + account() + "/adjust", body, "retry-1")
                .getBody(String.class).orElseThrow();

        assertEquals(first, replay, "the replay must return the stored answer");
        assertEquals("1", scalar("SELECT count(*)::text FROM transaction WHERE reason = 'Retry'"),
                "the replay must not book a second transaction");
    }

    @Test
    void sameKeyWithAnotherBodyIsAConflict() {
        String admin = TOKENS.staffAdmin();
        post(admin, "/v1/staff/accounts/" + account() + "/adjust",
                "{\"currency\": \"COINS\", \"direction\": \"CREDIT\", \"amount\": \"5.00\", \"reason\": \"A\"}", "conflict-1");

        HttpClientResponseException refusal = Api.refusal(client, write(admin,
                "/v1/staff/accounts/" + account() + "/adjust",
                "{\"currency\": \"COINS\", \"direction\": \"CREDIT\", \"amount\": \"6.00\", \"reason\": \"B\"}",
                "conflict-1"));

        assertProblem(refusal, HttpStatus.CONFLICT, ProblemCode.IDEMPOTENCY_CONFLICT);
    }

    @Test
    void resetRacingATransferNeverTakesABalanceBelowZero() throws Exception {
        UUID other = UUID.fromString("00000000-0000-4000-8000-0000000000a3");
        String admin = TOKENS.staffAdmin();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> reset = pool.submit(() -> {
                start.await();
                try {
                    client.toBlocking().exchange(write(admin, "/v1/staff/accounts/" + account() + "/reset",
                            "{\"currency\": \"COINS\", \"reason\": \"Race\"}", "race-reset"), String.class);
                } catch (HttpClientResponseException refused) {
                    // The reset may find nothing left to book after the transfer; it writes nothing then.
                }
                return null;
            });
            Future<?> move = pool.submit(() -> {
                start.await();
                try {
                    transfer(PLAYER, other, "12.50");
                } catch (RuntimeException refused) {
                    // The transfer may find the balance already reset; it writes nothing then.
                }
                return null;
            });
            start.countDown();
            reset.get(30, TimeUnit.SECONDS);
            move.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertTrue(new BigDecimal(balance(PLAYER, "COINS")).signum() >= 0, "the balance must never be negative");
    }

    @Test
    void registeringAClientWritesAStaffAction() throws Exception {
        UUID lobby = UUID.fromString("00000000-0000-4000-8000-0000000000d4");

        HttpResponse<String> response = post(TOKENS.staffAdmin(), "/v1/staff/clients",
                "{\"clientId\": \"" + lobby + "\", \"displayName\": \"paper-lobby-1\"}", "client-1");

        assertEquals(HttpStatus.CREATED, response.getStatus());
        assertEquals(1, staffActions("CLIENT_REGISTERED"), "one CLIENT_REGISTERED row must exist");
    }

    @Test
    void registeringTheSameClientAgainIsAConflict() {
        UUID lobby = UUID.fromString("00000000-0000-4000-8000-0000000000d5");
        String admin = TOKENS.staffAdmin();
        post(admin, "/v1/staff/clients", "{\"clientId\": \"" + lobby + "\", \"displayName\": \"one\"}", "client-2");

        HttpClientResponseException refusal = Api.refusal(client, write(admin, "/v1/staff/clients",
                "{\"clientId\": \"" + lobby + "\", \"displayName\": \"two\"}", "client-3"));

        assertProblem(refusal, HttpStatus.CONFLICT, ProblemCode.CLIENT_EXISTS);
    }

    @Test
    void replacingGrantsWithAnEmptyListRemovesThemAndWritesAStaffAction() throws Exception {
        HttpResponse<String> response = put(TOKENS.staffAdmin(), "/v1/staff/clients/" + APP + "/grants",
                "{\"grants\": []}", "grants-1");

        JsonNode answer = json.readValue(response.getBody(String.class).orElseThrow(), JsonNode.class);
        assertEquals(0, answer.get("grants").size(), "the client must have no grants afterwards");
        assertEquals(1, staffActions("GRANTS_REPLACED"));
    }

    @Test
    void duplicateGrantsAreRefused() {
        String duplicates = "{\"grants\": [{\"currency\": \"COINS\", \"permission\": \"MINT\"}, "
                + "{\"currency\": \"COINS\", \"permission\": \"MINT\"}]}";
        HttpClientResponseException refusal = Api.refusal(client, writeWith(
                HttpRequest.PUT("/v1/staff/clients/" + APP + "/grants", duplicates), TOKENS.staffAdmin(), "grants-2"));

        assertProblem(refusal, HttpStatus.BAD_REQUEST, ProblemCode.REQUEST_INVALID);
    }

    @Test
    void creatingACurrencyWritesAStaffAction() throws Exception {
        HttpResponse<String> response = post(TOKENS.staffAdmin(), "/v1/staff/currencies",
                "{\"code\": \"SHARDS\", \"nameSingular\": \"Shard\", \"namePlural\": \"Shards\", \"symbol\": \"S\", "
                        + "\"fractionDigits\": 0}", "currency-1");

        assertEquals(HttpStatus.CREATED, response.getStatus());
        assertEquals(1, staffActions("CURRENCY_CREATED"));
    }

    @Test
    void settingTheDefaultCurrencyMovesTheDefault() throws Exception {
        put(TOKENS.staffAdmin(), "/v1/staff/currencies/GEMS/default", null, "default-1");

        JsonNode items = json.readValue(read("/v1/staff/currencies"), JsonNode.class).get("items");
        JsonNode gems = null;
        for (JsonNode currency : items.values()) {
            if (currency.get("code").getStringValue().equals("GEMS")) {
                gems = currency;
            }
        }
        assertTrue(gems != null && gems.get("defaultCurrency").getBooleanValue(),
                "GEMS must be the default afterwards");
        assertEquals(1, staffActions("CURRENCY_DEFAULT_SET"));
    }

    // ---- helpers ----

    private UUID account() {
        return query(connection -> {
            try (var statement = connection.prepareStatement("SELECT id FROM account WHERE owner_uuid = ?")) {
                statement.setObject(1, PLAYER);
                try (var rows = statement.executeQuery()) {
                    rows.next();
                    return rows.getObject(1, UUID.class);
                }
            }
        });
    }

    /** The balance with the currency's two fraction digits, as text; no floating-point number is involved. */
    private String balance(UUID player, String currency) throws Exception {
        String stored = scalar("SELECT b.amount::text FROM balance b JOIN account a ON a.id = b.account_id "
                + "WHERE a.owner_uuid = '" + player + "' AND b.currency = '" + currency + "'");
        return new BigDecimal(stored).setScale(2).toPlainString();
    }

    private long staffActions(String action) throws Exception {
        return Long.parseLong(scalar("SELECT count(*)::text FROM staff_action WHERE action = '" + action + "'"));
    }

    private long staffActionsWithTransaction() throws Exception {
        return Long.parseLong(scalar("SELECT count(*)::text FROM staff_action WHERE transaction_id IS NOT NULL"));
    }

    private String scalar(String sql) throws Exception {
        try (Connection connection = connection();
             var statement = connection.createStatement();
             var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }

    private void mint(UUID player, String amountMinor) {
        MonetaryAmount amount = Money.of(new BigDecimal(amountMinor).movePointLeft(2),
                ColoeusCurrencyProvider.find("COINS").orElseThrow());
        TransactionCommand command = new TransactionCommand(TransactionKind.MINT, List.of(
                new Leg(new AccountRef.ClientSystem(AccountKind.SOURCE), amount.negate()),
                new Leg(new AccountRef.Player(player), amount)),
                "fixture", null, null, Map.of());
        transactions.inTransaction(connection -> booking.book(connection,
                new Caller(APP, "fixture", Set.of(), false), command, clock.instant(), "{}"));
    }

    /** A transfer booked by an admin caller through the booking service, as the client endpoint would book it. */
    private void transfer(UUID from, UUID to, String amount) {
        MonetaryAmount money = Money.of(new BigDecimal(amount), ColoeusCurrencyProvider.find("COINS").orElseThrow());
        TransactionCommand command = new TransactionCommand(TransactionKind.TRANSFER, List.of(
                new Leg(new AccountRef.Player(from), money.negate()),
                new Leg(new AccountRef.Player(to), money)),
                "race", from.toString(), null, Map.of());
        Caller admin = new Caller(UUID.fromString("00000000-0000-4000-8000-0000000000a1"), "sub",
                Set.of(Roles.ADMIN), true);
        transactions.inTransaction(connection -> booking.book(connection, admin, command, clock.instant(), "{}"));
    }

    private HttpResponse<String> post(String token, String path, String body, String key) {
        return client.toBlocking().exchange(write(token, path, body, key), String.class);
    }

    private HttpResponse<String> put(String token, String path, String body, String key) {
        return client.toBlocking().exchange(writeWith(HttpRequest.PUT(path, body == null ? "" : body), token, key),
                String.class);
    }

    private MutableHttpRequest<String> write(String token, String path, String body, String key) {
        return writeWith(HttpRequest.POST(path, body), token, key);
    }

    private MutableHttpRequest<String> writeWith(MutableHttpRequest<String> request, String token, String key) {
        request.bearerAuth(token).header("Content-Type", "application/json");
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return request;
    }

    private String read(String path) {
        return client.toBlocking().retrieve(Api.bearer(HttpRequest.GET(path), TOKENS.staffAdmin()));
    }
}
