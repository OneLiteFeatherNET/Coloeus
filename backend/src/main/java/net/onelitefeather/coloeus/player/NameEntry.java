package net.onelitefeather.coloeus.player;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

/**
 * One name in the history of a UUID.
 */
@Serdeable
public record NameEntry(String name, Instant firstSeen, Instant lastSeen, NameSource source) {
}
