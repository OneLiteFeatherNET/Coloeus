package net.onelitefeather.coloeus.player;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;
import org.junit.jupiter.api.Test;

class PlayerNameValidationTest {

    @Test
    void aSixteenCharacterNameIsAccepted() {
        assertDoesNotThrow(() -> PlayerNameService.requireName("abcdefghijklmnop"),
                "a Minecraft name may have 16 characters");
    }

    @Test
    void aSeventeenCharacterNameIsRefused() {
        assertEquals(ProblemCode.REQUEST_INVALID, refusal("abcdefghijklmnopq"),
                "a name longer than 16 characters is not a Minecraft name");
    }

    @Test
    void underscoresAndDigitsAreAccepted() {
        assertDoesNotThrow(() -> PlayerNameService.requireName("Steve_42"), "letters, digits and underscores are allowed");
    }

    @Test
    void aHyphenIsRefused() {
        assertEquals(ProblemCode.REQUEST_INVALID, refusal("Steve-42"), "a hyphen is not part of a Minecraft name");
    }

    @Test
    void anEmptyNameIsRefused() {
        assertEquals(ProblemCode.REQUEST_INVALID, refusal(""), "an empty name is not a name");
    }

    @Test
    void aMissingNameIsRefused() {
        assertEquals(ProblemCode.REQUEST_INVALID, refusal(null), "a request without a name is refused");
    }

    private static ProblemCode refusal(String name) {
        ProblemException failure = assertThrows(ProblemException.class, () -> PlayerNameService.requireName(name),
                "the name must be refused: " + name);
        return failure.code();
    }
}
