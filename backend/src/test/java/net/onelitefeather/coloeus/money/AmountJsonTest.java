package net.onelitefeather.coloeus.money;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import javax.money.CurrencyUnit;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;
import org.javamoney.moneta.Money;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AmountJsonTest {

    @BeforeEach
    void serveCoins() {
        ColoeusCurrencyProvider.replaceAll(List.of(new CurrencyDefinition("COINS", 2)));
    }

    @AfterEach
    void serveNothing() {
        ColoeusCurrencyProvider.replaceAll(List.of());
    }

    @Test
    void convertsDecimalStringToMoneyInTheDefinedCurrency() {
        Money money = new AmountJson("12.50", "COINS").toMoney();

        assertEquals("COINS", money.getCurrency().getCurrencyCode(), "the currency must come from the JSON form");
        assertEquals(0, new BigDecimal("12.50").compareTo(money.getNumberStripped()), "the amount must be 12.50");
    }

    @Test
    void rendersAmountWithExactlyTheFractionDigitsOfTheCurrency() {
        CurrencyUnit coins = ColoeusCurrencyProvider.find("COINS").orElseThrow();
        Money money = Money.of(new BigDecimal("12.5"), coins);

        AmountJson json = AmountJson.of(money);

        assertEquals("12.50", json.amount(), "12.5 COINS must be rendered with two fraction digits");
        assertEquals("COINS", json.currency(), "the currency code must be rendered as is");
    }

    @Test
    void rejectsUnknownCurrency() {
        ProblemException problem = assertThrows(ProblemException.class,
                () -> new AmountJson("1.00", "NOPE").toMoney());

        assertEquals(ProblemCode.CURRENCY_UNKNOWN, problem.code(), "an undefined currency must be CURRENCY_UNKNOWN");
    }

    @Test
    void rejectsScientificNotation() {
        ProblemException problem = assertThrows(ProblemException.class,
                () -> new AmountJson("1E+2", "COINS").toMoney());

        assertEquals(ProblemCode.AMOUNT_INVALID, problem.code(),
                "only plain decimal strings are accepted, so 1E+2 must be AMOUNT_INVALID");
    }

    @Test
    void rejectsNegativeSign() {
        ProblemException problem = assertThrows(ProblemException.class,
                () -> new AmountJson("-1.00", "COINS").toMoney());

        assertEquals(ProblemCode.AMOUNT_INVALID, problem.code(), "a negative amount must be AMOUNT_INVALID");
    }

    @Test
    void rejectsMissingAmount() {
        ProblemException problem = assertThrows(ProblemException.class,
                () -> new AmountJson(null, "COINS").toMoney());

        assertEquals(ProblemCode.AMOUNT_INVALID, problem.code(), "a missing amount must be AMOUNT_INVALID");
    }

    @Test
    void rejectsAmountWithTooManyFractionDigits() {
        ProblemException problem = assertThrows(ProblemException.class,
                () -> new AmountJson("1.005", "COINS").toMoney());

        assertEquals(ProblemCode.SCALE_EXCEEDED, problem.code(), "1.005 COINS must be SCALE_EXCEEDED");
    }
}
