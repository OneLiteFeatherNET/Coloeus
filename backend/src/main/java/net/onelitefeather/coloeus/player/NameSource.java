package net.onelitefeather.coloeus.player;

import io.micronaut.serde.annotation.Serdeable;

/**
 * How the register learned a name: a client reported a join, or the Mojang profile API answered a lookup.
 */
@Serdeable
public enum NameSource {
    JOIN,
    MOJANG
}
