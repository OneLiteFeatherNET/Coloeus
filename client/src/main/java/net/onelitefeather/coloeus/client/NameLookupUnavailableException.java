package net.onelitefeather.coloeus.client;

/**
 * The Mojang profile API failed or did not answer in time for an unknown name (problem code
 * {@code NAME_LOOKUP_UNAVAILABLE}).
 */
public final class NameLookupUnavailableException extends ColoeusProblemException {

    public NameLookupUnavailableException(int status, String detail) {
        super("NAME_LOOKUP_UNAVAILABLE", status, detail);
    }
}
