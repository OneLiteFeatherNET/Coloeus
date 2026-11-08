package net.onelitefeather.coloeus.paper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import javax.money.MonetaryAmount;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import net.onelitefeather.coloeus.client.AccountBalance;
import net.onelitefeather.coloeus.client.ColoeusClient;
import net.onelitefeather.coloeus.client.ColoeusException;
import net.onelitefeather.coloeus.client.CurrencyDefinition;
import net.onelitefeather.coloeus.client.InsufficientFundsException;
import net.onelitefeather.coloeus.client.PlayerAccount;
import net.onelitefeather.coloeus.client.PlayerUnknownException;
import net.onelitefeather.coloeus.client.Transaction;
import net.onelitefeather.coloeus.client.TransactionResult;
import org.bukkit.OfflinePlayer;

/**
 * The Vault economy of the default currency of the Coloeus network. Reads come from the balance cache, with a
 * synchronous fetch of at most 500 ms on a miss. Deposits are minted and withdrawals are burned, both synchronously
 * with one idempotency key per call and a budget of one second. Banks are not supported.
 */
final class ColoeusEconomy implements Economy {

    /** Timeout of a synchronous read on the server thread. */
    static final Duration CALL_TIMEOUT = ColoeusClient.DEFAULT_TIMEOUT;
    /** Total time of one deposit or withdrawal, including token acquisition and the retry. */
    static final Duration WRITE_BUDGET = Duration.ofSeconds(1);

    private static final String PROVIDER_NAME = "Coloeus";
    private static final String DEPOSIT_REASON = "Vault depositPlayer";
    private static final String WITHDRAW_REASON = "Vault withdrawPlayer";
    private static final String NO_BANKS = "Coloeus does not support bank accounts";

    private final ColoeusClient client;
    private final CurrencyDefinition currency;
    private final BalanceCache cache;
    private final Logger log;

    ColoeusEconomy(ColoeusClient client, CurrencyDefinition currency, BalanceCache cache, Logger log) {
        this.client = client;
        this.currency = currency;
        this.cache = cache;
        this.log = log;
    }

    /**
     * @param currencies every currency of the network
     * @return the one currency marked as default
     * @throws IllegalStateException when there is not exactly one default currency
     */
    static CurrencyDefinition defaultCurrency(List<CurrencyDefinition> currencies) {
        List<CurrencyDefinition> defaults = currencies.stream().filter(CurrencyDefinition::defaultCurrency).toList();
        if (defaults.size() != 1) {
            throw new IllegalStateException("Coloeus must mark exactly one currency as default, found " + defaults.size());
        }
        return defaults.get(0);
    }

    // ---- provider and currency

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return currency.fractionDigits();
    }

    @Override
    public String format(double amount) {
        return VaultAmounts.format(amount, currency);
    }

    @Override
    public String currencyNamePlural() {
        return currency.namePlural();
    }

    @Override
    public String currencyNameSingular() {
        return currency.nameSingular();
    }

    // ---- accounts

    @Override
    public boolean hasAccount(String playerName) {
        return resolve(playerName).map(this::accountExists).orElse(false);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return accountExists(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return hasAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    // ---- balances

    @Override
    public double getBalance(String playerName) {
        return resolve(playerName).map(this::balanceOf).orElse(0.0);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return balanceOf(player.getUniqueId());
    }

    @Override
    public double getBalance(String playerName, String worldName) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String worldName) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return resolve(playerName).map(id -> balanceOf(id) >= amount).orElse(false);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return balanceOf(player.getUniqueId()) >= amount;
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    // ---- writes

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return resolve(playerName)
                .map(id -> deposit(id, amount))
                .orElseGet(() -> failure(amount, 0.0, "unknown player name " + playerName));
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return deposit(player.getUniqueId(), amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return resolve(playerName)
                .map(id -> withdraw(id, amount))
                .orElseGet(() -> failure(amount, 0.0, "unknown player name " + playerName));
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return withdraw(player.getUniqueId(), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    // ---- banks: not supported in this version

    @Override
    public EconomyResponse createBank(String name, String player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    // ---- core

    private EconomyResponse deposit(UUID player, double amount) {
        return book(player, amount, true);
    }

    private EconomyResponse withdraw(UUID player, double amount) {
        return book(player, amount, false);
    }

    /**
     * Books one deposit (a mint) or withdrawal (a burn). A failure answers FAILURE and changes nothing; a success
     * takes the balance of the answer into the cache.
     */
    private EconomyResponse book(UUID player, double amount, boolean deposit) {
        if (!VaultAmounts.isUsable(amount)) {
            return failure(amount, cachedBalance(player), "the amount must be a finite, non-negative number");
        }
        MonetaryAmount money = VaultAmounts.round(amount, currency);
        if (money.isZero()) {
            return success(amount, balanceOf(player));
        }
        double before = cachedBalance(player);
        Transaction transaction = deposit
                ? Transaction.mint(player, money, DEPOSIT_REASON)
                : Transaction.burn(player, money, WITHDRAW_REASON);
        try {
            TransactionResult result = client.transact(transaction, UUID.randomUUID().toString(), WRITE_BUDGET);
            double expected = before + (deposit ? amount : -amount);
            return success(amount, applyBooked(player, result, expected));
        } catch (InsufficientFundsException e) {
            log.fine("Withdrawal of " + amount + " refused for " + player + ": insufficient funds");
            return failure(amount, before, e.getMessage());
        } catch (ColoeusException e) {
            log.warning((deposit ? "Deposit" : "Withdrawal") + " of " + amount + " for " + player
                    + " failed, the answer is FAILURE: " + e.getMessage());
            return failure(amount, before, e.getMessage());
        }
    }

    /**
     * Takes the balance of the booked player from the answer. A booking touches exactly one player balance; if the
     * answer does not show exactly one, the cache entry is dropped and the expected balance is returned.
     */
    private double applyBooked(UUID player, TransactionResult result, double expected) {
        List<AccountBalance> lines = result.balances();
        if (lines.size() == 1) {
            MonetaryAmount balance = lines.get(0).amount();
            cache.put(player, balance);
            return VaultAmounts.toDouble(balance);
        }
        cache.evict(player);
        log.warning("Coloeus answered a booking of " + player + " with " + lines.size()
                + " player balances instead of one; the cache entry was dropped");
        return expected;
    }

    private double balanceOf(UUID player) {
        return cachedOrFetch(player).map(VaultAmounts::toDouble).orElse(0.0);
    }

    private double cachedBalance(UUID player) {
        return cache.get(player).map(entry -> VaultAmounts.toDouble(entry.balance())).orElse(0.0);
    }

    private boolean accountExists(UUID player) {
        return cachedOrFetch(player).isPresent();
    }

    /**
     * @return the cached balance, or a synchronous read within {@link #CALL_TIMEOUT} that is then cached; empty when
     *         the read fails, in which case nothing is stored
     */
    private Optional<MonetaryAmount> cachedOrFetch(UUID player) {
        Optional<BalanceCache.Entry> cached = cache.get(player);
        if (cached.isPresent()) {
            return Optional.of(cached.get().balance());
        }
        try {
            PlayerAccount account = client.playerAccount(player, CALL_TIMEOUT);
            MonetaryAmount balance = VaultAmounts.balanceOf(account, currency);
            cache.put(player, balance);
            return Optional.of(balance);
        } catch (ColoeusException e) {
            log.warning("Balance of " + player + " could not be read from Coloeus, answering 0: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * @return the UUID of a name: from the name map of this server, or from the register within {@link #CALL_TIMEOUT}
     */
    private Optional<UUID> resolve(String name) {
        Optional<UUID> known = cache.uuidOf(name);
        if (known.isPresent()) {
            return known;
        }
        try {
            UUID player = client.playerByName(name, CALL_TIMEOUT);
            cache.remember(name, player);
            return Optional.of(player);
        } catch (PlayerUnknownException e) {
            log.fine("Name " + name + " is unknown to Coloeus");
            return Optional.empty();
        } catch (ColoeusException e) {
            log.warning("Name " + name + " could not be resolved, answering FAILURE or 0: " + e.getMessage());
            return Optional.empty();
        }
    }

    private static EconomyResponse success(double amount, double balance) {
        return new EconomyResponse(amount, balance, ResponseType.SUCCESS, null);
    }

    private static EconomyResponse failure(double amount, double balance, String message) {
        return new EconomyResponse(amount, balance, ResponseType.FAILURE, message);
    }

    private static EconomyResponse notImplemented() {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, NO_BANKS);
    }
}
