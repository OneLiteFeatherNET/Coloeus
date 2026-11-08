package net.onelitefeather.coloeus.auth;

import io.micronaut.serde.annotation.Serdeable;

/**
 * One grant of a client: a permission on a currency.
 */
@Serdeable
public record GrantView(String currency, Permission permission) {
}
