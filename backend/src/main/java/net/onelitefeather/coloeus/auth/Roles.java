package net.onelitefeather.coloeus.auth;

/**
 * The Entra app roles of the service, as they appear in the {@code roles} claim of an access token.
 * Constants, so that {@code @Secured} can name them.
 */
public final class Roles {

    /** Service-to-service calls of registered clients (the token's {@code azp}). */
    public static final String CLIENT = "Coloeus.Client";

    /** Management: currencies, the client registry, and everything a moderator may not do. */
    public static final String ADMIN = "Coloeus.Admin";

    /** Read-only staff. */
    public static final String MODERATOR = "Coloeus.Moderator";

    private Roles() {
    }
}
