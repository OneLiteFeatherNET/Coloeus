package net.onelitefeather.coloeus.player;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/**
 * A profile as the Mojang API answers it. The id arrives without dashes ({@code 32 hex digits}).
 */
@Serdeable
public record MojangProfile(String id, String name) {

    /**
     * @throws IllegalArgumentException when the id is neither 32 hex digits nor a dashed UUID
     */
    public UUID uuid() {
        if (id != null && id.matches("[0-9a-fA-F]{32}")) {
            return UUID.fromString(id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(12, 16)
                    + "-" + id.substring(16, 20) + "-" + id.substring(20));
        }
        if (id != null && id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            return UUID.fromString(id);
        }
        throw new IllegalArgumentException("not a Mojang profile id: " + id);
    }
}
