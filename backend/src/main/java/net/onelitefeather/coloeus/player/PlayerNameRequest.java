package net.onelitefeather.coloeus.player;

import io.micronaut.serde.annotation.Serdeable;

/**
 * A join report: the name the player joined with.
 */
@Serdeable
public record PlayerNameRequest(String name) {
}
