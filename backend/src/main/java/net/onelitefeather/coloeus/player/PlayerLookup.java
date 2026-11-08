package net.onelitefeather.coloeus.player;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The answer to a name lookup: the UUID that most recently held the name, the name it held, and every name that UUID
 * has held (most recent first).
 */
@Serdeable
public record PlayerLookup(UUID uuid, String name, Instant lastSeen, NameSource source, List<NameEntry> history) {
}
