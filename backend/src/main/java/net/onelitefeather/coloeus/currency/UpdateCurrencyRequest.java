package net.onelitefeather.coloeus.currency;

import io.micronaut.serde.annotation.Serdeable;

/**
 * Names and symbol of a currency. {@code fractionDigits} is optional and only accepted when it equals the stored
 * value; any other value is rejected with {@code CURRENCY_SCALE_IMMUTABLE}.
 */
@Serdeable
public record UpdateCurrencyRequest(
        String nameSingular,
        String namePlural,
        String symbol,
        Integer fractionDigits) {
}
