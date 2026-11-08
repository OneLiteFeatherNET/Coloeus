package net.onelitefeather.coloeus.auth;

import io.micronaut.serde.annotation.Serdeable;

/**
 * Registers a client by its Entra client id. The id arrives as text and is checked as a UUID by the service.
 */
@Serdeable
public record RegisterClientRequest(String clientId, String displayName) {
}
