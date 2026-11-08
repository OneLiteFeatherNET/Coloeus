package net.onelitefeather.coloeus.client;

import java.util.UUID;
import javax.money.MonetaryAmount;

/**
 * A transaction to book. A {@link Kind#MINT} credits {@code to} from the client's source account, a {@link Kind#BURN}
 * debits {@code from} into the client's sink account, and a {@link Kind#TRANSFER} moves money between two players.
 *
 * @param kind   the kind of transaction
 * @param from   the debited player, null for a mint
 * @param to     the credited player, null for a burn
 * @param amount the positive amount, in the currency of the network
 * @param reason free text stored with the transaction, at most 255 characters; may be null
 */
public record Transaction(Kind kind, UUID from, UUID to, MonetaryAmount amount, String reason) {

    /** The kinds of transaction the client books. */
    public enum Kind {
        MINT, BURN, TRANSFER
    }

    public Transaction {
        if (kind == null) {
            throw new IllegalArgumentException("kind must be set");
        }
        if (amount == null || !amount.isPositive()) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    public static Transaction mint(UUID to, MonetaryAmount amount, String reason) {
        return new Transaction(Kind.MINT, null, to, amount, reason);
    }

    public static Transaction burn(UUID from, MonetaryAmount amount, String reason) {
        return new Transaction(Kind.BURN, from, null, amount, reason);
    }

    public static Transaction transfer(UUID from, UUID to, MonetaryAmount amount, String reason) {
        return new Transaction(Kind.TRANSFER, from, to, amount, reason);
    }
}
