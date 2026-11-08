package net.onelitefeather.coloeus.admin;

/**
 * The staff member who acts in a staff write, taken from the delegated token of the call.
 *
 * @param oid         the Entra object id: the actor of the write and the scope of its idempotency keys
 * @param displayName the {@code name} claim, or null when the token carries none
 */
public record StaffActor(String oid, String displayName) {
}
