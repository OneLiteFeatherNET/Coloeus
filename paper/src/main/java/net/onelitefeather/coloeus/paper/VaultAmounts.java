package net.onelitefeather.coloeus.paper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import javax.money.MonetaryAmount;
import net.onelitefeather.coloeus.client.CurrencyDefinition;
import net.onelitefeather.coloeus.client.PlayerAccount;
import org.javamoney.moneta.Money;

/**
 * The conversions at the Vault boundary. Vault passes amounts as {@code double}; this is the only place where such an
 * amount is rounded, to the fraction digits of the default currency.
 */
final class VaultAmounts {

    private VaultAmounts() {
    }

    /**
     * @param amount a Vault amount
     * @return whether the amount may be booked: finite and not negative
     */
    static boolean isUsable(double amount) {
        return Double.isFinite(amount) && amount >= 0;
    }

    /**
     * Converts a usable Vault amount with its shortest decimal representation and rounds it half to even.
     *
     * @param amount   a finite, non-negative amount
     * @param currency the default currency
     * @return the rounded amount; zero when it rounds to zero
     */
    static MonetaryAmount round(double amount, CurrencyDefinition currency) {
        BigDecimal exact = BigDecimal.valueOf(amount).setScale(currency.fractionDigits(), RoundingMode.HALF_EVEN);
        return Money.of(exact, currency.unit());
    }

    static double toDouble(MonetaryAmount amount) {
        return amount.getNumber().doubleValue();
    }

    /**
     * @return the balance of the currency in the account; zero when the account holds no row of it
     */
    static MonetaryAmount balanceOf(PlayerAccount account, CurrencyDefinition currency) {
        return account.balanceIn(currency.code()).orElseGet(() -> Money.zero(currency.unit()));
    }

    /**
     * @return the amount with the fraction digits and the symbol of the currency, for example {@code 12.50 c}
     */
    static String format(double amount, CurrencyDefinition currency) {
        if (!Double.isFinite(amount)) {
            return String.valueOf(amount);
        }
        String digits = BigDecimal.valueOf(amount)
                .setScale(currency.fractionDigits(), RoundingMode.HALF_EVEN)
                .toPlainString();
        return digits + " " + currency.symbol();
    }
}
