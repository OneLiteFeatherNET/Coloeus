package net.onelitefeather.coloeus.client;

/**
 * The server did not answer in time, could not be reached or answered with a 5xx status. Nothing is known about
 * whether a write was booked; a write may be retried with the same idempotency key.
 */
public class ColoeusUnavailableException extends ColoeusException {

    public ColoeusUnavailableException(String message) {
        super(message);
    }

    public ColoeusUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
