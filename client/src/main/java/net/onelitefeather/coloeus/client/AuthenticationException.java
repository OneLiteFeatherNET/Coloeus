package net.onelitefeather.coloeus.client;

/**
 * The call could not be authenticated: the server rejected the bearer token (problem code {@code UNAUTHORIZED}), or
 * the identity provider refused the client credentials.
 */
public final class AuthenticationException extends ColoeusProblemException {

    public AuthenticationException(int status, String detail) {
        super("UNAUTHORIZED", status, detail);
    }
}
