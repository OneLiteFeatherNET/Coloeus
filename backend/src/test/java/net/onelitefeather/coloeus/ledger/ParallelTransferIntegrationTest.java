package net.onelitefeather.coloeus.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.context.ApplicationContext;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.onelitefeather.coloeus.account.AccountService;
import net.onelitefeather.coloeus.auth.Caller;
import net.onelitefeather.coloeus.auth.Roles;
import net.onelitefeather.coloeus.currency.CreateCurrencyRequest;
import net.onelitefeather.coloeus.currency.CurrencyService;
import net.onelitefeather.coloeus.money.AmountJson;
import net.onelitefeather.coloeus.support.LedgerDatabase;
import net.onelitefeather.coloeus.support.PostgresTestContainer;
import net.onelitefeather.coloeus.support.TestDatabase;
import net.onelitefeather.coloeus.support.TestTokens;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Two service instances (application contexts) on one database book the same hot accounts at the same time. Each
 * transfer must be applied exactly once, none lost, and the ledger must stay balanced.
 */
@Tag("integration")
class ParallelTransferIntegrationTest {

    private static final PostgreSQLContainer POSTGRES = PostgresTestContainer.start();
    private static final TestTokens TOKENS = TestTokens.start();

    /** A registered client with MINT and TRANSFER_ON_BEHALF on COINS; it acts for ALICE, the payer of every transfer. */
    private static final Caller CLIENT = new Caller(TestTokens.CLIENT_APP, "parallel-test", Set.of(Roles.CLIENT), false);
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final int TRANSFERS = 100;

    @BeforeEach
    void freshLedger() throws SQLException {
        LedgerDatabase.migrate(POSTGRES);
        LedgerDatabase.truncate(POSTGRES);
    }

    @AfterAll
    static void stopIdentityProvider() {
        TOKENS.close();
    }

    @Test
    void hundredParallelTransfersAcrossTwoReplicasApplyEachOnceAndKeepTheZeroSum() throws Exception {
        ApplicationContext first = replica();
        ApplicationContext second = replica();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            first.getBean(CurrencyService.class)
                    .create(new CreateCurrencyRequest("COINS", "Coin", "Coins", "C", 2));
            registerClientWithGrants();
            TransactionService firstService = first.getBean(TransactionService.class);
            TransactionService secondService = second.getBean(TransactionService.class);
            JsonMapper json = first.getBean(JsonMapper.class);
            firstService.post(CLIENT, "mint", parse(json, mint(ALICE, "100.00")));

            CountDownLatch startGate = new CountDownLatch(1);
            List<Future<String>> bookings = new ArrayList<>();
            for (int i = 0; i < TRANSFERS; i++) {
                TransactionService replicaService = i % 2 == 0 ? firstService : secondService;
                JsonNode body = parse(json, transfer(ALICE, BOB, "1.00"));
                String key = "transfer-" + i;
                bookings.add(pool.submit(() -> {
                    startGate.await();
                    return replicaService.post(CLIENT, key, body);
                }));
            }
            startGate.countDown();
            for (Future<String> booking : bookings) {
                booking.get(120, TimeUnit.SECONDS);
            }

            AccountService accounts = first.getBean(AccountService.class);
            assertEquals("0.00", coins(accounts, ALICE), "all 100 transfers must leave the payer with zero");
            assertEquals("100.00", coins(accounts, BOB), "every transfer must credit the payee exactly once");
            assertEquals(TRANSFERS + 1L, transactionCount(),
                    "each transfer and the mint must be stored exactly once");
            assertTrue(sumByCurrency().compareTo(BigDecimal.ZERO) == 0,
                    "the sum of all balances in COINS must be zero");
            assertEquals(0L, negativePlayerBalances(), "no player balance may fall below zero");
        } finally {
            pool.shutdownNow();
            second.close();
            first.close();
        }
    }

    private static ApplicationContext replica() {
        return ApplicationContext.run(new LinkedHashMap<String, Object>(TestDatabase.properties(POSTGRES, TOKENS)));
    }

    /** The registry of the client that books: it may mint and act for players, and nothing else. */
    private static void registerClientWithGrants() throws SQLException {
        try (Connection connection = POSTGRES.createConnection("");
             PreparedStatement client = connection.prepareStatement(
                     "INSERT INTO client (client_id, display_name, created_at) VALUES (?, 'parallel test', now())");
             PreparedStatement grants = connection.prepareStatement(
                     "INSERT INTO client_grant (client_id, currency, permission) VALUES (?, 'COINS', ?)")) {
            client.setObject(1, TestTokens.CLIENT_APP);
            client.executeUpdate();
            for (String permission : List.of("MINT", "TRANSFER_ON_BEHALF")) {
                grants.setObject(1, TestTokens.CLIENT_APP);
                grants.setString(2, permission);
                grants.executeUpdate();
            }
        }
    }

    private static JsonNode parse(JsonMapper json, String text) throws IOException {
        return json.readValue(text, JsonNode.class);
    }

    private static String coins(AccountService accounts, UUID player) {
        return accounts.playerAccount(player).balances().stream()
                .filter(amount -> amount.currency().equals("COINS"))
                .map(AmountJson::amount)
                .findFirst()
                .orElseThrow();
    }

    private static String mint(UUID player, String amount) {
        return "{\"kind\":\"MINT\",\"to\":{\"player\":\"" + player + "\"},"
                + "\"amount\":{\"amount\":\"" + amount + "\",\"currency\":\"COINS\"}}";
    }

    private static String transfer(UUID from, UUID to, String amount) {
        return "{\"kind\":\"TRANSFER\",\"from\":{\"player\":\"" + from + "\"},\"to\":{\"player\":\"" + to + "\"},"
                + "\"actor\":\"" + from + "\","
                + "\"amount\":{\"amount\":\"" + amount + "\",\"currency\":\"COINS\"}}";
    }

    private static long transactionCount() throws SQLException {
        return scalarLong("SELECT count(*) FROM transaction");
    }

    private static long negativePlayerBalances() throws SQLException {
        return scalarLong("SELECT count(*) FROM balance b JOIN account a ON a.id = b.account_id "
                + "WHERE a.kind = 'PLAYER' AND b.amount < 0");
    }

    private static BigDecimal sumByCurrency() throws SQLException {
        try (Connection connection = POSTGRES.createConnection("");
             PreparedStatement statement = connection.prepareStatement("SELECT SUM(amount) FROM balance");
             ResultSet rows = statement.executeQuery()) {
            rows.next();
            BigDecimal sum = rows.getBigDecimal(1);
            return sum == null ? BigDecimal.ZERO : sum;
        }
    }

    private static long scalarLong(String sql) throws SQLException {
        try (Connection connection = POSTGRES.createConnection("");
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
