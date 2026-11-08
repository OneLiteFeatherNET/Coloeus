package net.onelitefeather.coloeus.ledger;

/**
 * Another booking stored the key first. Thrown inside the booking transaction, which rolls back; the caller then
 * answers from the stored key.
 */
final class IdempotencyKeyTaken extends RuntimeException {

    IdempotencyKeyTaken(String key) {
        super("idempotency key " + key + " is already stored");
    }
}
