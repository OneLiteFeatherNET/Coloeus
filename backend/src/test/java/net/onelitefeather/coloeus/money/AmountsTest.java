package net.onelitefeather.coloeus.money;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.MathContext;
import javax.money.CurrencyUnit;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;
import org.javamoney.moneta.CurrencyUnitBuilder;
import org.javamoney.moneta.Money;
import org.junit.jupiter.api.Test;

class AmountsTest {

    private final CurrencyUnit coins = CurrencyUnitBuilder.of("COINS", "test").setDefaultFractionDigits(2).build();

    @Test
    void acceptsAmountWithExactlyTheFractionDigitsOfTheCurrency() {
        Money money = Amounts.fromDecimal(new BigDecimal("12.50"), coins);

        assertEquals(0, new BigDecimal("12.50").compareTo(money.getNumberStripped().setScale(2)),
                "12.50 COINS must be accepted unchanged");
    }

    @Test
    void acceptsAmountWithFewerFractionDigits() {
        Money money = Amounts.fromDecimal(new BigDecimal("7"), coins);

        assertEquals(0, BigDecimal.valueOf(7).compareTo(money.getNumberStripped()), "7 COINS must be accepted");
    }

    @Test
    void rejectsZero() {
        ProblemException problem = assertThrows(ProblemException.class,
                () -> Amounts.fromDecimal(new BigDecimal("0.00"), coins));

        assertEquals(ProblemCode.AMOUNT_INVALID, problem.code(), "zero must be rejected as AMOUNT_INVALID");
    }

    @Test
    void rejectsNegativeAmount() {
        ProblemException problem = assertThrows(ProblemException.class,
                () -> Amounts.fromDecimal(new BigDecimal("-1.00"), coins));

        assertEquals(ProblemCode.AMOUNT_INVALID, problem.code(), "negative amounts must be rejected as AMOUNT_INVALID");
    }

    @Test
    void rejectsMoreFractionDigitsThanTheCurrencyAllows() {
        ProblemException problem = assertThrows(ProblemException.class,
                () -> Amounts.fromDecimal(new BigDecimal("1.005"), coins));

        assertEquals(ProblemCode.SCALE_EXCEEDED, problem.code(),
                "1.005 COINS has three fraction digits and must be rejected as SCALE_EXCEEDED");
    }

    @Test
    void rejectsExcessDigitsInsteadOfRoundingThem() {
        ProblemException problem = assertThrows(ProblemException.class,
                () -> Amounts.fromDecimal(new BigDecimal("0.999"), coins));

        assertEquals(ProblemCode.SCALE_EXCEEDED, problem.code(),
                "0.999 must not be rounded up to 1.00 but rejected");
    }

    @Test
    void rejectsMoreSignificantDigitsThanTheContextHolds() {
        BigDecimal thirtyFiveDigits = new BigDecimal("1" + "0".repeat(34) + ".5");

        ProblemException problem = assertThrows(ProblemException.class,
                () -> Amounts.fromDecimal(thirtyFiveDigits, coins));

        assertEquals(ProblemCode.AMOUNT_INVALID, problem.code(),
                "an amount beyond 34 significant digits would be rounded by Money, so it must be rejected");
    }

    @Test
    void contextIsMoneyWithDecimal128() {
        assertEquals(Money.class, Amounts.CONTEXT.getAmountType(), "the shared context must describe Money");
        assertEquals(MathContext.DECIMAL128, Amounts.CONTEXT.get(MathContext.class),
                "the shared context must carry MathContext.DECIMAL128");
    }
}
