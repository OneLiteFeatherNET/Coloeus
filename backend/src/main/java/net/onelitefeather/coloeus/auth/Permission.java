package net.onelitefeather.coloeus.auth;

import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;

import java.util.Arrays;

/**
 * A right a registered client holds per currency. The names are stored in {@code client_grant.permission}.
 */
public enum Permission {

    MINT,
    BURN,
    TRANSFER_ON_BEHALF,
    READ;

    /** Reads a permission name from a request; anything else is a {@link ProblemCode#REQUEST_INVALID}. */
    public static Permission parse(String name) {
        return Arrays.stream(values())
                .filter(permission -> permission.name().equals(name))
                .findFirst()
                .orElseThrow(() -> ProblemException.of(ProblemCode.REQUEST_INVALID,
                        "permission must be one of MINT, BURN, TRANSFER_ON_BEHALF, READ"));
    }
}
