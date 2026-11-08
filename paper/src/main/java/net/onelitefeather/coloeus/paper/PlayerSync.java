package net.onelitefeather.coloeus.paper;

import java.time.Duration;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import net.onelitefeather.coloeus.client.ColoeusClient;
import net.onelitefeather.coloeus.client.ColoeusException;
import net.onelitefeather.coloeus.client.CurrencyDefinition;
import net.onelitefeather.coloeus.client.PlayerAccount;

/**
 * Keeps the balance cache and the server's player name register up to date without blocking the server thread. A join
 * returns at once; the name report and the balance fetch run on the executor, and a failure is logged and changes
 * nothing else.
 */
final class PlayerSync {

    /** Timeout of the background reads; they run off the server thread, so they may wait longer than a Vault call. */
    static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(5);

    private final ColoeusClient client;
    private final BalanceCache cache;
    private final CurrencyDefinition currency;
    private final Executor executor;
    private final Logger log;

    PlayerSync(ColoeusClient client, BalanceCache cache, CurrencyDefinition currency, Executor executor, Logger log) {
        this.client = client;
        this.cache = cache;
        this.currency = currency;
        this.executor = executor;
        this.log = log;
    }

    /**
     * Handles a join: the name becomes known to this server at once, the report and the balance follow in the
     * background.
     */
    void join(UUID player, String name) {
        cache.remember(name, player);
        executor.execute(() -> {
            report(player, name);
            fetch(player);
        });
    }

    /** Fetches the balances of the given players; a failed fetch leaves the entry as it was. */
    void refresh(Collection<UUID> players) {
        players.forEach(this::fetch);
    }

    private void report(UUID player, String name) {
        try {
            client.reportName(player, name, UUID.randomUUID().toString(), ColoeusEconomy.WRITE_BUDGET);
        } catch (ColoeusException e) {
            log.warning("Name report of " + name + " to Coloeus failed: " + e.getMessage());
        }
    }

    private void fetch(UUID player) {
        try {
            PlayerAccount account = client.playerAccount(player, ASYNC_TIMEOUT);
            cache.put(player, VaultAmounts.balanceOf(account, currency));
        } catch (ColoeusException e) {
            log.warning("Balance of " + player + " could not be read from Coloeus: " + e.getMessage());
        }
    }
}
