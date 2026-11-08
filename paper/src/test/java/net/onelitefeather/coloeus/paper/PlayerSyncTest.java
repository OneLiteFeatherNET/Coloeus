package net.onelitefeather.coloeus.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import net.onelitefeather.coloeus.client.ColoeusUnavailableException;
import net.onelitefeather.coloeus.client.CurrencyDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.javamoney.moneta.Money;

class PlayerSyncTest {

    private static final UUID ALEX = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
    private static final String ALEX_NAME = "Alex";

    private FakeColoeusClient client;
    private BalanceCache cache;
    private Queue<Runnable> background;
    private PlayerSync sync;
    private CurrencyDefinition coins;

    @BeforeEach
    void setUp() {
        coins = new CurrencyDefinition("COINS", "coin", "coins", "c", 2, true);
        client = new FakeColoeusClient(coins);
        cache = new BalanceCache(new MutableClock());
        background = new ArrayDeque<>();
        // The background work is queued and runs only when the test runs it: nothing waits on a thread.
        Executor queue = background::add;
        sync = new PlayerSync(client, cache, coins, queue, Logger.getAnonymousLogger());
    }

    @Test
    void joinReturnsBeforeTheReportRuns() {
        sync.join(ALEX, ALEX_NAME);

        assertTrue(client.reports().isEmpty(), "the join must not report the name on the calling thread");
        runBackground();
        assertEquals(List.of(ALEX), client.reports(), "the queued task must report the name");
    }

    @Test
    void joinRemembersTheNameBeforeTheBackgroundWorkRuns() {
        sync.join(ALEX, ALEX_NAME);

        assertEquals(ALEX, cache.uuidOf("alex").orElseThrow(
                        () -> new AssertionError("the name must be known as soon as the join happens")),
                "a joining name must resolve without waiting for the report");
    }

    @Test
    void joinFetchesTheBalanceInTheBackground() {
        client.setBalance(ALEX, "10.00");

        sync.join(ALEX, ALEX_NAME);
        assertTrue(cache.get(ALEX).isEmpty(), "the balance must not be fetched on the calling thread");
        runBackground();

        assertEquals(0, new BigDecimal("10.00").compareTo(cachedBalance(ALEX)),
                "the background fetch must store the balance of the joining player");
    }

    @Test
    void failedReportLeavesTheBalanceCacheUnchanged() {
        client.setBalance(ALEX, "10.00");
        cache.put(ALEX, money("10.00"));
        client.failReports(new ColoeusUnavailableException("timed out in the fake"));

        sync.join(ALEX, ALEX_NAME);
        runBackground();

        assertEquals(0, new BigDecimal("10.00").compareTo(cachedBalance(ALEX)),
                "a failed name report must not change the balance cache");
    }

    @Test
    void refreshChangesTheCachedBalanceToTheServerValue() {
        cache.put(ALEX, money("10.00"));
        client.setBalance(ALEX, "15.00");

        sync.refresh(List.of(ALEX));

        assertEquals(0, new BigDecimal("15.00").compareTo(cachedBalance(ALEX)),
                "a refresh must replace the cached balance with the balance on the server");
    }

    @Test
    void failedRefreshKeepsTheCachedEntry() {
        cache.put(ALEX, money("10.00"));
        client.failAccountReads(new ColoeusUnavailableException("timed out in the fake"));

        sync.refresh(List.of(ALEX));

        assertEquals(0, new BigDecimal("10.00").compareTo(cachedBalance(ALEX)),
                "a failed refresh must keep the last known balance");
    }

    private void runBackground() {
        Runnable task;
        while ((task = background.poll()) != null) {
            task.run();
        }
    }

    private BigDecimal cachedBalance(UUID player) {
        return cache.get(player).map(entry -> entry.balance().getNumber().numberValue(BigDecimal.class))
                .orElseThrow(() -> new AssertionError("the cache must hold a balance of " + player));
    }

    private Money money(String value) {
        return Money.of(new BigDecimal(value), coins.unit());
    }
}
