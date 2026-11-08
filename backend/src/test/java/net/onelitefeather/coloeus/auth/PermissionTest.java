package net.onelitefeather.coloeus.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;
import org.junit.jupiter.api.Test;

class PermissionTest {

    @Test
    void aKnownNameParsesToItsPermission() {
        assertEquals(Permission.TRANSFER_ON_BEHALF, Permission.parse("TRANSFER_ON_BEHALF"),
                "a permission name from the request must parse to the same permission");
    }

    @Test
    void anUnknownNameIsRequestInvalid() {
        ProblemException failure = assertThrows(ProblemException.class, () -> Permission.parse("read"),
                "names are case sensitive and an unknown name is refused");

        assertEquals(ProblemCode.REQUEST_INVALID, failure.code(), "an unknown permission is REQUEST_INVALID");
    }
}
