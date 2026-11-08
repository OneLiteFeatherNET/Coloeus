package net.onelitefeather.coloeus.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import javax.money.MonetaryAmount;
import net.onelitefeather.coloeus.client.CurrencyDefinition;
import org.junit.jupiter.api.Test;

class VaultAmountsTest {

    private static final CurrencyDefinition COINS = new CurrencyDefinition("COINS", "coin", "coins", "c", 2, true);

    @Test
    void halfToEvenRoundsOneOhOneFiveToOneOhTwo() {
        assertEquals(0, new BigDecimal("1.02").compareTo(scaled(VaultAmounts.round(1.015, COINS))),
                "1.015 has an odd digit before the half and must round to the even 1.02");
    }

    @Test
    void halfToEvenRoundsOneOhTwoFiveToOneOhTwo() {
        assertEquals(0, new BigDecimal("1.02").compareTo(scaled(VaultAmounts.round(1.025, COINS))),
                "1.025 has an even digit before the half and must round to 1.02");
    }

    @Test
    void thousandthRoundsToZero() {
        assertTrue(VaultAmounts.round(0.001, COINS).isZero(), "0.001 must round to zero in two fraction digits");
    }

    @Test
    void shortestDecimalKeepsDoubleArtifactsOutOfTheAmount() {
        assertEquals(0, new BigDecimal("0.30").compareTo(scaled(VaultAmounts.round(0.1 + 0.2, COINS))),
                "0.1 + 0.2 must be booked as 0.30, not as a binary approximation");
    }

    @Test
    void negativeNotANumberAndInfiniteAmountsAreNotUsable() {
        assertFalse(VaultAmounts.isUsable(-1.0), "a negative amount must not be usable");
        assertFalse(VaultAmounts.isUsable(Double.NaN), "NaN must not be usable");
        assertFalse(VaultAmounts.isUsable(Double.POSITIVE_INFINITY), "a positive infinite amount must not be usable");
        assertFalse(VaultAmounts.isUsable(Double.NEGATIVE_INFINITY), "a negative infinite amount must not be usable");
    }

    @Test
    void zeroIsUsable() {
        assertTrue(VaultAmounts.isUsable(0.0), "zero is a usable amount; it is handled as no request");
    }

    @Test
    void formatShowsTwoFractionDigitsAndTheSymbol() {
        assertEquals("12.50 c", VaultAmounts.format(12.5, COINS), "12.5 must be shown as 12.50 with the symbol c");
    }

    @Test
    void doubleOfAnAmountIsItsValue() {
        assertEquals(2.5, VaultAmounts.toDouble(VaultAmounts.round(2.5, COINS)), 0.0,
                "the double of a rounded amount must be its value");
    }

    private static BigDecimal scaled(MonetaryAmount amount) {
        return amount.getNumber().numberValue(BigDecimal.class);
    }
}
