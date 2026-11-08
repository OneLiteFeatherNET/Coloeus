package net.onelitefeather.coloeus.ledger;

/**
 * The kinds of transaction a client can book. The names are the stored values of {@code transaction.kind}.
 */
public enum TransactionKind {
    TRANSFER,
    MINT,
    BURN,
    BATCH
}
