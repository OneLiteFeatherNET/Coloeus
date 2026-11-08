package net.onelitefeather.coloeus.client;

/** The debited player balance would fall below zero (problem code {@code INSUFFICIENT_FUNDS}). */
public final class InsufficientFundsException extends ColoeusProblemException {

    public InsufficientFundsException(int status, String detail) {
        super("INSUFFICIENT_FUNDS", status, detail);
    }
}
