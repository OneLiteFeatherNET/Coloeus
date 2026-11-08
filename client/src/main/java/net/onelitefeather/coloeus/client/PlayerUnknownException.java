package net.onelitefeather.coloeus.client;

/** The player name is known neither to the register nor to the Mojang profile API (problem code {@code PLAYER_UNKNOWN}). */
public final class PlayerUnknownException extends ColoeusProblemException {

    public PlayerUnknownException(int status, String detail) {
        super("PLAYER_UNKNOWN", status, detail);
    }
}
