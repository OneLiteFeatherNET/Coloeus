package net.onelitefeather.coloeus.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.money.CurrencyUnit;
import javax.money.MonetaryAmount;
import io.micronaut.serde.annotation.Serdeable;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;
import org.javamoney.moneta.Money;

/**
 * JSON mapper: {@code {"amount": "12.50", "currency": "COINS"}}. The amount is a plain decimal string, never a JSON
 * number, so no JSON library can lose precision. The only JSON-facing type that may expose {@link BigDecimal}
 * concepts; its public signatures use strings.
 */
@Serdeable
public record AmountJson(String amount, String currency) {

    private static final Pattern PLAIN_DECIMAL = Pattern.compile("[0-9]+(\\.[0-9]+)?");

    /** Renders a money amount with exactly the fraction digits of its currency. */
    public static AmountJson of(MonetaryAmount amount) {
        Money money = Money.from(amount);
        CurrencyUnit currency = money.getCurrency();
        BigDecimal value = money.getNumber().numberValueExact(BigDecimal.class)
                .setScale(Amounts.fractionDigits(currency), RoundingMode.UNNECESSARY);
        return new AmountJson(value.toPlainString(), currency.getCurrencyCode());
    }

    /** Validates the JSON form and converts it to {@link Money} in a currency this replica serves. */
    public Money toMoney() {
        return toMoney(ColoeusCurrencyProvider::find);
    }

    /**
     * Validates the JSON form and converts it to {@link Money}, resolving the currency through {@code currencies}
     * (the service passes a lookup that reloads the provider once on a miss).
     */
    public Money toMoney(Function<String, Optional<CurrencyUnit>> currencies) {
        if (amount == null || !PLAIN_DECIMAL.matcher(amount).matches()) {
            throw ProblemException.of(ProblemCode.AMOUNT_INVALID,
                    "amount must be a plain decimal string such as \"12.50\"");
        }
        CurrencyUnit unit = (currency == null ? Optional.<CurrencyUnit>empty() : currencies.apply(currency))
                .orElseThrow(() -> ProblemException.of(ProblemCode.CURRENCY_UNKNOWN,
                        "currency " + currency + " is not defined"));
        return Amounts.fromDecimal(new BigDecimal(amount), unit);
    }
}
