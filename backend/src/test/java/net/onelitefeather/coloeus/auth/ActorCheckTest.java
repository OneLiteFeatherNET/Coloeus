package net.onelitefeather.coloeus.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import java.util.UUID;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;
import org.junit.jupiter.api.Test;

class ActorCheckTest {

    private static final UUID OWNER = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID OTHER = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID CLIENT = UUID.fromString("00000000-0000-4000-8000-0000000000c1");

    @Test
    void theDebitedPlayerAsActorPasses() {
        assertDoesNotThrow(() -> ActorCheck.require(client(), OWNER, OWNER.toString()),
                "a client may act for the player it names as actor");
    }

    @Test
    void anotherPlayerAsActorIsAMismatch() {
        assertEquals(ProblemCode.ACTOR_MISMATCH, mismatch(client(), OWNER, OTHER.toString()),
                "a client may not act for a player other than the actor");
    }

    @Test
    void aMissingActorIsAMismatch() {
        assertEquals(ProblemCode.ACTOR_MISMATCH, mismatch(client(), OWNER, null),
                "without an actor the client cannot show it acts for the player");
    }

    @Test
    void anActorThatIsNoUuidIsAMismatch() {
        assertEquals(ProblemCode.ACTOR_MISMATCH, mismatch(client(), OWNER, "Steve42"),
                "an actor that is not a player UUID cannot be the debited player");
    }

    @Test
    void anAdminMayActForAnyPlayer() {
        assertDoesNotThrow(() -> ActorCheck.require(admin(), OWNER, OTHER.toString()),
                "an admin may act for any player");
    }

    @Test
    void anAdminMayActWithoutAnyActor() {
        assertDoesNotThrow(() -> ActorCheck.require(admin(), OWNER, null),
                "an admin needs no actor at all");
    }

    private static ProblemCode mismatch(Caller caller, UUID owner, String actor) {
        ProblemException failure = assertThrows(ProblemException.class,
                () -> ActorCheck.require(caller, owner, actor),
                "the actor rule must refuse this request");
        return failure.code();
    }

    private static Caller client() {
        return new Caller(CLIENT, "subject", Set.of(Roles.CLIENT), false);
    }

    private static Caller admin() {
        return new Caller(CLIENT, "subject", Set.of(Roles.ADMIN), true);
    }
}
