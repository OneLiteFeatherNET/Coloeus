package net.onelitefeather.coloeus.admin;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import net.onelitefeather.coloeus.auth.GrantRequest;

/**
 * The bodies of the staff writes. Fields arrive as text and are checked by the services, so every refusal is a problem
 * document.
 */
public final class StaffBodies {

    private StaffBodies() {
    }

    /** {@code POST /v1/staff/accounts/{id}/reset}: a currency, or null for every currency with a positive balance. */
    @Serdeable
    public record Reset(String currency, String reason) {
    }

    /** {@code POST /v1/staff/accounts/{id}/adjust}. */
    @Serdeable
    public record Adjust(String currency, String direction, String amount, String reason) {
    }

    /** {@code PUT /v1/staff/clients/{clientId}/grants}: the complete new set of grants. */
    @Serdeable
    public record Grants(List<GrantRequest> grants) {
    }
}
