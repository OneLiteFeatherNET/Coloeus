package net.onelitefeather.coloeus.money;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import javax.money.CurrencyUnit;
import org.javamoney.moneta.CurrencyUnitBuilder;
import org.javamoney.moneta.Money;
import org.junit.jupiter.api.Test;

class AmountColumnsTest {

    private final CurrencyUnit coins = CurrencyUnitBuilder.of("COINS", "test").setDefaultFractionDigits(2).build();

    @Test
    void storesTheExactDecimalValue() {
        Money money = Money.of(new BigDecimal("12.50"), coins);

        BigDecimal column = AmountColumns.toColumn(money);

        assertEquals(0, new BigDecimal("12.50").compareTo(column), "the column value must equal 12.50");
    }

    @Test
    void readsStoredValueAndRendersItWithTheFractionDigitsOfTheCurrency() {
        Money money = AmountColumns.fromColumn(new BigDecimal("12.5000000"), coins);

        assertEquals("12.50", AmountJson.of(money).amount(),
                "a stored 12.5000000 must be read as 12.50 COINS");
    }

    @Test
    void refusesToRoundStoredValueWithMoreDigitsThanTheCurrency() {
        assertThrows(ArithmeticException.class,
                () -> AmountColumns.fromColumn(new BigDecimal("12.505"), coins),
                "a stored value with more digits than the currency is corrupt and must not be rounded");
    }
}
