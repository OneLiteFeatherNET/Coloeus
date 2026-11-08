package net.onelitefeather.coloeus.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MojangProfileTest {

    private static final UUID STEVE = UUID.fromString("f7c77d99-9f15-4a66-a87d-c4a51ef30d19");

    @Test
    void anUndashedIdIsReadAsTheDashedUuid() {
        assertEquals(STEVE, new MojangProfile("f7c77d999f154a66a87dc4a51ef30d19", "Steve").uuid(),
                "Mojang sends ids without dashes; they must become the standard UUID");
    }

    @Test
    void aDashedIdIsReadAsItIs() {
        assertEquals(STEVE, new MojangProfile("f7c77d99-9f15-4a66-a87d-c4a51ef30d19", "Steve").uuid(),
                "an id that is already dashed must not be changed");
    }

    @Test
    void anIdThatIsNoUuidIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new MojangProfile("not-an-id", "Steve").uuid(),
                "a malformed id must not be guessed at");
    }
}
