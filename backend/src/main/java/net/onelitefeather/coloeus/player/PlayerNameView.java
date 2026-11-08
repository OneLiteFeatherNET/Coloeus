package net.onelitefeather.coloeus.player;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/**
 * The register entry of one UUID and name.
 */
@Serdeable
public record PlayerNameView(UUID uuid, String name, Instant firstSeen, Instant lastSeen, NameSource source) {
}
