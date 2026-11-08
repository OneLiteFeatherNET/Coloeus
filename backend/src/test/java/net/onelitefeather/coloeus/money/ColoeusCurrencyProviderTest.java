package net.onelitefeather.coloeus.money;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import javax.money.Monetary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ColoeusCurrencyProviderTest {

    @BeforeEach
    void serveNothing() {
        ColoeusCurrencyProvider.replaceAll(List.of());
    }

    @AfterEach
    void serveNothingAgain() {
        ColoeusCurrencyProvider.replaceAll(List.of());
    }

    @Test
    void jsr354ResolvesReplacedCurrencyWithItsFractionDigits() {
        ColoeusCurrencyProvider.replaceAll(List.of(new CurrencyDefinition("COINS", 2)));

        assertEquals(2, Monetary.getCurrency("COINS").getDefaultFractionDigits(),
                "Monetary.getCurrency must return COINS with the stored two fraction digits");
    }

    @Test
    void servesEachCurrencyWithItsOwnFractionDigits() {
        ColoeusCurrencyProvider.replaceAll(List.of(
                new CurrencyDefinition("COINS", 2),
                new CurrencyDefinition("GEMS", 0)));

        assertEquals(0, ColoeusCurrencyProvider.find("GEMS").orElseThrow().getDefaultFractionDigits(),
                "GEMS must keep its own zero fraction digits");
    }

    @Test
    void replaceAllDropsCurrenciesThatAreNoLongerStored() {
        ColoeusCurrencyProvider.replaceAll(List.of(new CurrencyDefinition("COINS", 2)));

        ColoeusCurrencyProvider.replaceAll(List.of(new CurrencyDefinition("GEMS", 0)));

        assertFalse(ColoeusCurrencyProvider.find("COINS").isPresent(),
                "a currency missing from the new set must no longer be served");
        assertTrue(ColoeusCurrencyProvider.find("GEMS").isPresent(), "the new currency must be served");
    }

    @Test
    void unknownCodeIsNotServed() {
        ColoeusCurrencyProvider.replaceAll(List.of(new CurrencyDefinition("COINS", 2)));

        assertFalse(Monetary.isCurrencyAvailable("NOPE"), "an undefined code must not resolve");
    }
}
