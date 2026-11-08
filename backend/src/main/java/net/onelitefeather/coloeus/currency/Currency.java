package net.onelitefeather.coloeus.currency;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

/**
 * A currency as stored and served. The fraction digits are fixed at creation.
 */
@Serdeable
public record Currency(
        String code,
        String nameSingular,
        String namePlural,
        String symbol,
        int fractionDigits,
        boolean defaultCurrency,
        Instant createdAt,
        Instant updatedAt) {
}
