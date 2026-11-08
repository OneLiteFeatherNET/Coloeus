package net.onelitefeather.coloeus.paper;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.milkbowl.vault.economy.Economy;
import net.onelitefeather.coloeus.client.ColoeusClient;
import net.onelitefeather.coloeus.client.ColoeusException;
import net.onelitefeather.coloeus.client.CurrencyDefinition;
import net.onelitefeather.coloeus.client.HttpColoeusClient;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Registers the Coloeus economy with Vault while the plugin loads, so it is in place before any plugin is enabled. If
 * the configuration is incomplete, or Coloeus has no single default currency, the plugin disables itself and registers
 * nothing, so the server keeps its previous economy.
 */
public final class ColoeusPlugin extends JavaPlugin {

    /** Timeout of the one read at startup, which runs on the server thread. */
    static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(5);
    /** The balances of online players are refreshed every 30 seconds (600 ticks). */
    private static final long REFRESH_TICKS = 30L * 20L;

    private ColoeusClient client;
    private CurrencyDefinition currency;
    private BalanceCache cache;
    /** The reason the economy could not be built in {@link #onLoad()}, or {@code null} when it was registered. */
    private String startupFailure;
    private ExecutorService io;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        ConnectionSettings settings = ConnectionSettings.resolve(getConfig()::getString, System::getenv);
        List<String> missing = settings.missing();
        if (!missing.isEmpty()) {
            fail("missing configuration: " + String.join(", ", missing));
            return;
        }

        Clock clock = Clock.systemUTC();
        try {
            client = HttpColoeusClient.create(settings.toClientConfig(), clock);
            currency = ColoeusEconomy.defaultCurrency(client.currencies(STARTUP_TIMEOUT));
        } catch (IllegalArgumentException | IllegalStateException | ColoeusException e) {
            fail(e.getMessage());
            return;
        }

        cache = new BalanceCache(clock);
        ColoeusEconomy economy = new ColoeusEconomy(client, currency, cache, getLogger());
        getServer().getServicesManager().register(Economy.class, economy, this, ServicePriority.Highest);
        getLogger().info("Vault economy " + currency.code() + " registered at Highest priority");
    }

    @Override
    public void onEnable() {
        if (startupFailure != null) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        io = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "coloeus-io");
            thread.setDaemon(true);
            return thread;
        });
        PlayerSync sync = new PlayerSync(client, cache, currency, io, getLogger());

        getServer().getPluginManager().registerEvents(new JoinListener(sync), this);
        getServer().getScheduler().runTaskTimer(this, () -> refreshOnline(sync), REFRESH_TICKS, REFRESH_TICKS);
        refreshOnline(sync);
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (io != null) {
            io.shutdownNow();
        }
    }

    /** Collects the online players on the server thread and refreshes their balances in the background. */
    private void refreshOnline(PlayerSync sync) {
        List<UUID> online = getServer().getOnlinePlayers().stream().map(Player::getUniqueId).toList();
        io.execute(() -> sync.refresh(online));
    }

    private void fail(String reason) {
        startupFailure = reason;
        getLogger().severe("Coloeus disabled, no economy is registered: " + reason);
    }
}
