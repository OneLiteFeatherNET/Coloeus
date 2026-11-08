package net.onelitefeather.coloeus.currency;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record CreateCurrencyRequest(
        String code,
        String nameSingular,
        String namePlural,
        String symbol,
        Integer fractionDigits) {
}
