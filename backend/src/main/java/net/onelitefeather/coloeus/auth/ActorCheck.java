package net.onelitefeather.coloeus.auth;

import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;

import java.util.UUID;

/**
 * Who may debit a player's account on that player's behalf (design decision 7). The rule, as the transaction endpoint
 * applies it: an admin may act for anyone; any other caller needs the {@link Permission#TRANSFER_ON_BEHALF} grant
 * ({@link GrantService#require}) and must name the debited player as actor, which this helper checks.
 */
public final class ActorCheck {

    private ActorCheck() {
    }

    /**
     * @param caller       the caller of the request
     * @param debitedOwner the owner UUID of the player account that is debited
     * @param actor        the {@code actor} of the request, as sent; may be null
     * @throws ProblemException {@link ProblemCode#ACTOR_MISMATCH} when the caller is not an admin and the actor is not
     *                          the debited player
     */
    public static void require(Caller caller, UUID debitedOwner, String actor) {
        if (caller.admin()) {
            return;
        }
        if (actor == null || !debitedOwner.equals(parse(actor))) {
            throw ProblemException.of(ProblemCode.ACTOR_MISMATCH,
                    "the actor must be the debited player " + debitedOwner + " unless the caller is an admin");
        }
    }

    private static UUID parse(String actor) {
        try {
            return UUID.fromString(actor.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
