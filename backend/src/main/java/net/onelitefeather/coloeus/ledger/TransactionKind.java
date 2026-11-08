package net.onelitefeather.coloeus.ledger;

/**
 * The kinds of transaction. The names are the stored values of {@code transaction.kind}. Clients book TRANSFER, MINT,
 * BURN and BATCH; RESET and ADJUST are the money writes of the staff API and are refused on the client endpoint.
 */
public enum TransactionKind {
    TRANSFER,
    MINT,
    BURN,
    BATCH,
    RESET,
    ADJUST
}
