package net.onelitefeather.coloeus.money;

import java.math.BigDecimal;
import java.math.MathContext;
import javax.money.CurrencyUnit;
import javax.money.MonetaryContext;
import javax.money.MonetaryContextBuilder;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;
import org.javamoney.moneta.Money;

/**
 * Amount rules shared by every boundary. Incoming amounts are validated, never rounded.
 * <p>
 * Public signatures use JSR 354 types only; {@link BigDecimal} stays inside {@link AmountColumns} and
 * {@link AmountJson} (enforced by an architecture test).
 */
public final class Amounts {

    /** Context of every {@link Money} in the service: 34 significant digits, {@link MathContext#DECIMAL128}. */
    public static final MonetaryContext CONTEXT = MonetaryContextBuilder.of(Money.class)
            .set(MathContext.class, MathContext.DECIMAL128)
            .build();

    private Amounts() {
    }

    /** Fraction digits a currency allows; amounts in it must not have more. */
    public static int fractionDigits(CurrencyUnit currency) {
        return currency.getDefaultFractionDigits();
    }

    /**
     * Validates a decimal amount for a currency and returns it as {@link Money}.
     * Rejects zero and negative values ({@code AMOUNT_INVALID}), values with more significant digits than
     * {@link MathContext#DECIMAL128} holds ({@code AMOUNT_INVALID}) and more fraction digits than the currency
     * allows ({@code SCALE_EXCEEDED}). Never rounds.
     */
    static Money fromDecimal(BigDecimal value, CurrencyUnit currency) {
        if (value.signum() <= 0) {
            throw ProblemException.of(ProblemCode.AMOUNT_INVALID, "amount must be greater than zero");
        }
        if (value.precision() > MathContext.DECIMAL128.getPrecision()) {
            throw ProblemException.of(ProblemCode.AMOUNT_INVALID,
                    "amount must not have more than " + MathContext.DECIMAL128.getPrecision() + " significant digits");
        }
        int digits = fractionDigits(currency);
        if (value.scale() > digits) {
            throw ProblemException.of(ProblemCode.SCALE_EXCEEDED,
                    currency.getCurrencyCode() + " allows " + digits + " fraction digits, the amount has " + value.scale());
        }
        return Money.of(value, currency, CONTEXT);
    }
}
