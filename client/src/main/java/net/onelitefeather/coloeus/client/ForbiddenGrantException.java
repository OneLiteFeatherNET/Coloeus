package net.onelitefeather.coloeus.client;

/** The client holds no grant for the permission of the call (problem code {@code FORBIDDEN_GRANT}). */
public final class ForbiddenGrantException extends ColoeusProblemException {

    public ForbiddenGrantException(int status, String detail) {
        super("FORBIDDEN_GRANT", status, detail);
    }
}
