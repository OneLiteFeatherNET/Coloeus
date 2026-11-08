package net.onelitefeather.coloeus.client;

import javax.money.CurrencyUnit;
import org.javamoney.moneta.CurrencyUnitBuilder;

/**
 * A currency of the network as {@code GET /v1/currencies} serves it.
 *
 * @param code          the currency code, 3 to 8 upper case letters
 * @param nameSingular  the singular name, for example {@code coin}
 * @param namePlural    the plural name, for example {@code coins}
 * @param symbol        the symbol shown after an amount
 * @param fractionDigits the fraction digits of the currency, 0 to 8
 * @param defaultCurrency whether this is the default currency of the network
 */
public record CurrencyDefinition(String code, String nameSingular, String namePlural, String symbol,
                                 int fractionDigits, boolean defaultCurrency) {

    /**
     * @return a JSR 354 unit of this currency with its fraction digits; not registered globally
     */
    public CurrencyUnit unit() {
        return CurrencyUnitBuilder.of(code, "coloeus").setDefaultFractionDigits(fractionDigits).build();
    }
}
