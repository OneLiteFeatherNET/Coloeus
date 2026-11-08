package net.onelitefeather.coloeus.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import net.onelitefeather.coloeus.client.ColoeusUnavailableException;
import net.onelitefeather.coloeus.client.CurrencyDefinition;
import net.onelitefeather.coloeus.client.Transaction;
import org.bukkit.OfflinePlayer;
import org.javamoney.moneta.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ColoeusEconomyTest {

    private static final UUID ALEX = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
    private static final UUID SAM = UUID.fromString("5e6c1f2a-3b4d-4e5f-8a9b-0c1d2e3f4a5b");
    private static final String ALEX_NAME = "Alex";

    private FakeColoeusClient client;
    private BalanceCache cache;
    private ColoeusEconomy economy;
    private OfflinePlayer alex;

    @BeforeEach
    void setUp() {
        CurrencyDefinition coins = new CurrencyDefinition("COINS", "coin", "coins", "c", 2, true);
        client = new FakeColoeusClient(coins);
        cache = new BalanceCache(new MutableClock());
        economy = new ColoeusEconomy(client, coins, cache, Logger.getAnonymousLogger());
        alex = FakeOfflinePlayer.of(ALEX, ALEX_NAME);
    }

    @Test
    void depositMintsTheAmountAndAnswersTheResultingBalance() {
        client.setBalance(ALEX, "10.00");

        EconomyResponse response = economy.depositPlayer(alex, 2.50);

        assertEquals(ResponseType.SUCCESS, response.type, "a booked deposit must answer SUCCESS");
        assertEquals(0, new BigDecimal("12.50").compareTo(BigDecimal.valueOf(response.balance)),
                "the answer must carry the resulting balance 12.50");
        assertEquals(Transaction.Kind.MINT, client.transactions().get(0).kind(), "a deposit must be a MINT");
        assertEquals(0, new BigDecimal("12.50").compareTo(cachedBalance(ALEX)), "the cache must hold 12.50");
    }

    @Test
    void depositWithoutMintGrantAnswersFailureAndKeepsTheBalance() {
        client.setBalance(ALEX, "10.00");
        economy.getBalance(alex);
        client.revokeGrant();

        EconomyResponse response = economy.depositPlayer(alex, 2.50);

        assertEquals(ResponseType.FAILURE, response.type, "a deposit without the MINT grant must answer FAILURE");
        assertEquals(0, new BigDecimal("10.00").compareTo(client.balance(ALEX)),
                "the ledger balance must be unchanged");
        assertEquals(0, new BigDecimal("10.00").compareTo(cachedBalance(ALEX)),
                "the cache must be unchanged after a failed deposit");
    }

    @Test
    void withdrawalBurnsTheAmountAndAnswersTheResultingBalance() {
        client.setBalance(ALEX, "3.00");

        EconomyResponse response = economy.withdrawPlayer(alex, 1.00);

        assertEquals(ResponseType.SUCCESS, response.type, "a booked withdrawal must answer SUCCESS");
        assertEquals(0, new BigDecimal("2.00").compareTo(BigDecimal.valueOf(response.balance)),
                "the answer must carry the resulting balance 2.00");
        assertEquals(Transaction.Kind.BURN, client.transactions().get(0).kind(), "a withdrawal must be a BURN");
    }

    @Test
    void insufficientFundsAnswersFailureAndKeepsTheCachedBalance() {
        client.setBalance(ALEX, "3.00");
        economy.getBalance(alex);

        EconomyResponse response = economy.withdrawPlayer(alex, 4.00);

        assertEquals(ResponseType.FAILURE, response.type, "a withdrawal above the balance must answer FAILURE");
        assertEquals(0, new BigDecimal("3.00").compareTo(cachedBalance(ALEX)),
                "the cache must still hold 3.00 after insufficient funds");
    }

    @Test
    void unavailableColoeusAnswersFailureAndChangesNothing() {
        client.setBalance(ALEX, "3.00");
        economy.getBalance(alex);
        client.failNextTransaction(new ColoeusUnavailableException("timed out in the fake"));

        EconomyResponse response = economy.withdrawPlayer(alex, 1.00);

        assertEquals(ResponseType.FAILURE, response.type, "a timed out withdrawal must answer FAILURE, never a guess");
        assertEquals(0, new BigDecimal("3.00").compareTo(cachedBalance(ALEX)),
                "the cache must keep the last known balance");
    }

    @Test
    void negativeDepositIsRefusedWithoutARequest() {
        client.setBalance(ALEX, "10.00");
        economy.getBalance(alex);

        EconomyResponse response = economy.depositPlayer(alex, -5.00);

        assertEquals(ResponseType.FAILURE, response.type, "a negative deposit must answer FAILURE");
        assertTrue(client.transactions().isEmpty(), "a negative deposit must send no request");
        assertEquals(0, new BigDecimal("10.00").compareTo(cachedBalance(ALEX)),
                "the cached balance must stay 10.00");
    }

    @Test
    void notANumberAndInfiniteAmountsAreRefusedWithoutARequest() {
        assertEquals(ResponseType.FAILURE, economy.depositPlayer(alex, Double.NaN).type,
                "NaN must answer FAILURE");
        assertEquals(ResponseType.FAILURE, economy.withdrawPlayer(alex, Double.POSITIVE_INFINITY).type,
                "an infinite amount must answer FAILURE");
        assertTrue(client.transactions().isEmpty(), "invalid amounts must send no request");
    }

    @Test
    void amountThatRoundsToZeroAnswersSuccessWithTheCachedBalanceAndSendsNothing() {
        client.setBalance(ALEX, "10.00");
        economy.getBalance(alex);

        EconomyResponse response = economy.depositPlayer(alex, 0.001);

        assertEquals(ResponseType.SUCCESS, response.type, "an amount that rounds to zero must answer SUCCESS");
        assertEquals(0, new BigDecimal("10.00").compareTo(BigDecimal.valueOf(response.balance)),
                "the answer must carry the cached balance");
        assertTrue(client.transactions().isEmpty(), "an amount that rounds to zero must send no request");
    }

    @Test
    void roundingHalfToEvenBooksOneOhTwoForBothVectors() {
        client.setBalance(ALEX, "0.00");

        economy.depositPlayer(alex, 1.015);
        economy.depositPlayer(alex, 1.025);

        assertEquals(0, new BigDecimal("1.02").compareTo(amountOf(0)),
                "1.015 must be booked as 1.02 with HALF_EVEN rounding");
        assertEquals(0, new BigDecimal("1.02").compareTo(amountOf(1)),
                "1.025 must be booked as 1.02 with HALF_EVEN rounding");
    }

    @Test
    void bankCallsAnswerNotImplementedWithoutARequest() {
        assertFalse(economy.hasBankSupport(), "the provider must report no bank support");

        EconomyResponse response = economy.createBank("treasury", alex);

        assertEquals(ResponseType.NOT_IMPLEMENTED, response.type, "a bank call must answer NOT_IMPLEMENTED");
        assertEquals(ResponseType.NOT_IMPLEMENTED, economy.bankBalance("treasury").type,
                "every bank call must answer NOT_IMPLEMENTED");
        assertTrue(client.transactions().isEmpty() && client.accountReads() == 0,
                "bank calls must send no request");
    }

    @Test
    void worldOverloadsGiveTheSameAnswerAsTheOverloadsWithoutWorld() {
        client.setBalance(ALEX, "10.00");
        EconomyResponse withWorld = economy.depositPlayer(alex, "survival", 1.00);

        client.setBalance(ALEX, "10.00");
        cache.evict(ALEX);
        EconomyResponse withoutWorld = economy.depositPlayer(alex, 1.00);

        assertEquals(withoutWorld.type, withWorld.type, "the world must not change the response type");
        assertEquals(ResponseType.SUCCESS, withWorld.type, "the deposit into a world must be booked");
        assertEquals(0, new BigDecimal("11.00").compareTo(BigDecimal.valueOf(withWorld.balance)),
                "the deposit into a world must book the same balance as without world");
        assertEquals(withoutWorld.balance, withWorld.balance, 0.0,
                "both overloads must answer the same resulting balance");
    }

    @Test
    void unknownNameAnswersFailureWithoutBookingAnything() {
        EconomyResponse response = economy.depositPlayer("Nobody123", 1.00);

        assertEquals(ResponseType.FAILURE, response.type, "an unknown name must answer FAILURE");
        assertTrue(client.transactions().isEmpty(), "an unknown name must book nothing");
    }

    @Test
    void knownNameIsResolvedFromTheNameMapWithoutALookup() {
        client.setBalance(ALEX, "7.00");
        cache.remember(ALEX_NAME, ALEX);

        double balance = economy.getBalance("alex");

        assertEquals(7.00, balance, 0.0, "a name in the map must answer the cached balance of its player");
        assertEquals(0, client.nameLookups(), "a name in the map must not be looked up at Coloeus");
    }

    @Test
    void cachedBalanceAnswersWithoutARequest() {
        cache.put(ALEX, money("10.00"));

        assertEquals(10.00, economy.getBalance(alex), 0.0, "a cached balance must be the answer");
        assertEquals(0, client.accountReads(), "a cache hit must send no request");
    }

    @Test
    void cacheMissForAnOfflinePlayerFetchesOnceAndStoresTheBalance() {
        client.setBalance(SAM, "4.25");
        OfflinePlayer sam = FakeOfflinePlayer.of(SAM, "Sam");

        double first = economy.getBalance(sam);
        double second = economy.getBalance(sam);

        assertEquals(4.25, first, 0.0, "the first read must answer the fetched balance");
        assertEquals(4.25, second, 0.0, "the second read must answer the stored balance");
        assertEquals(1, client.accountReads(), "a cache miss must fetch exactly once");
    }

    @Test
    void failedFetchAnswersZeroAndStoresNothing() {
        client.setBalance(SAM, "4.25");
        client.failAccountReads(new ColoeusUnavailableException("timed out in the fake"));
        OfflinePlayer sam = FakeOfflinePlayer.of(SAM, "Sam");

        assertEquals(0.0, economy.getBalance(sam), 0.0, "an unreachable Coloeus must answer 0");
        assertTrue(cache.get(SAM).isEmpty(), "a failed fetch must store nothing");
    }

    @Test
    void hasComparesTheCachedBalance() {
        client.setBalance(ALEX, "10.00");

        assertTrue(economy.has(alex, 10.00), "a balance equal to the amount must pass has");
        assertFalse(economy.has(alex, 10.01), "a balance below the amount must fail has");
    }

    @Test
    void hasAccountIsFalseForANameThatDoesNotResolve() {
        assertFalse(economy.hasAccount("Nobody123"), "a name that does not resolve must not be an account");
    }

    @Test
    void formatShowsTheFractionDigitsAndTheSymbolOfTheCurrency() {
        assertEquals("12.50 c", economy.format(12.5), "format must show two fraction digits and the symbol");
        assertEquals("coins", economy.currencyNamePlural(), "the plural name must come from the currency");
        assertEquals("coin", economy.currencyNameSingular(), "the singular name must come from the currency");
        assertEquals(2, economy.fractionalDigits(), "the fraction digits must come from the currency");
    }

    @Test
    void defaultCurrencyIsTheOneMarkedAsDefault() {
        CurrencyDefinition coins = new CurrencyDefinition("COINS", "coin", "coins", "c", 2, true);
        CurrencyDefinition gems = new CurrencyDefinition("GEMS", "gem", "gems", "g", 0, false);

        assertEquals("COINS", ColoeusEconomy.defaultCurrency(List.of(gems, coins)).code(),
                "the currency marked as default must be chosen");
    }

    @Test
    void noDefaultCurrencyIsRefused() {
        CurrencyDefinition gems = new CurrencyDefinition("GEMS", "gem", "gems", "g", 0, false);

        assertThrows(IllegalStateException.class, () -> ColoeusEconomy.defaultCurrency(List.of(gems)),
                "a network without a default currency must be refused");
    }

    @Test
    void twoDefaultCurrenciesAreRefused() {
        CurrencyDefinition coins = new CurrencyDefinition("COINS", "coin", "coins", "c", 2, true);
        CurrencyDefinition gems = new CurrencyDefinition("GEMS", "gem", "gems", "g", 0, true);

        assertThrows(IllegalStateException.class, () -> ColoeusEconomy.defaultCurrency(List.of(coins, gems)),
                "two default currencies must be refused");
    }

    private BigDecimal cachedBalance(UUID player) {
        return cache.get(player).map(entry -> entry.balance().getNumber().numberValue(BigDecimal.class)).orElseThrow(
                () -> new AssertionError("the cache must hold a balance of " + player));
    }

    private BigDecimal amountOf(int index) {
        return client.transactions().get(index).amount().getNumber().numberValue(BigDecimal.class);
    }

    private static Money money(String value) {
        return Money.of(new BigDecimal(value), new CurrencyDefinition("COINS", "coin", "coins", "c", 2, true).unit());
    }
}
