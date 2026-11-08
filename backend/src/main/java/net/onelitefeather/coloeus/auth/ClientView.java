package net.onelitefeather.coloeus.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A registered client with its grants. The grants are always present, an empty list when there are none.
 */
@Serdeable
public record ClientView(
        UUID clientId,
        String displayName,
        Instant createdAt,
        @JsonInclude(JsonInclude.Include.ALWAYS) List<GrantView> grants) {
}
