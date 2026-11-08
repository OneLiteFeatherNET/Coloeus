package net.onelitefeather.coloeus.ledger;

import java.util.UUID;

/**
 * The scope of an idempotency key (design decision 6 of add-admin-web): {@code client:<client_id>} for the writes of a
 * registered client, {@code staff:<oid>} for the writes of a staff member. The same key in two scopes is two keys.
 */
public final class IdempotencyScope {

    private IdempotencyScope() {
    }

    public static String client(UUID clientId) {
        return "client:" + clientId;
    }

    public static String staff(String oid) {
        return "staff:" + oid;
    }
}
