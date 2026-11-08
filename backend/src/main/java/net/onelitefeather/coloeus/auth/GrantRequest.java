package net.onelitefeather.coloeus.auth;

import io.micronaut.serde.annotation.Serdeable;

/**
 * One grant of a replacement request. The permission arrives as text and is checked by {@link Permission#parse}, so
 * an unknown name answers with a problem document.
 */
@Serdeable
public record GrantRequest(String currency, String permission) {
}
