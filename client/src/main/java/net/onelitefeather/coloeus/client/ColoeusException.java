package net.onelitefeather.coloeus.client;

/**
 * Base of every failure of a call to the Coloeus API.
 */
public class ColoeusException extends RuntimeException {

    public ColoeusException(String message) {
        super(message);
    }

    public ColoeusException(String message, Throwable cause) {
        super(message, cause);
    }
}
