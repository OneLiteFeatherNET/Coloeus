package net.onelitefeather.coloeus.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import javax.money.CurrencyUnit;
import javax.money.MonetaryAmount;
import org.javamoney.moneta.Money;

/**
 * Database mapper between {@code NUMERIC(38, 8)} columns and {@link Money}. The only place besides
 * {@link AmountJson} where {@link BigDecimal} is part of a public signature.
 */
public final class AmountColumns {

    private AmountColumns() {
    }

    /** The value to store in a NUMERIC column. */
    public static BigDecimal toColumn(MonetaryAmount amount) {
        return Money.from(amount).getNumber().numberValueExact(BigDecimal.class);
    }

    /**
     * Reads a stored value. It is rendered with exactly the currency's fraction digits; a stored value with more
     * digits than the currency allows is corrupt data and fails instead of being rounded.
     */
    public static Money fromColumn(BigDecimal stored, CurrencyUnit currency) {
        BigDecimal exact = stored.setScale(Amounts.fractionDigits(currency), RoundingMode.UNNECESSARY);
        return Money.of(exact, currency, Amounts.CONTEXT);
    }
}
