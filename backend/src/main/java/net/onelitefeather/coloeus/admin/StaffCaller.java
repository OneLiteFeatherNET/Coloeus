package net.onelitefeather.coloeus.admin;

import java.util.Set;

/**
 * A verified delegated staff token of a {@code /v1/staff} call: who acts, and which staff app roles the token carries.
 *
 * @param actor the staff member (oid and display name)
 * @param roles the app roles of the token
 */
public record StaffCaller(StaffActor actor, Set<String> roles) {

    public StaffCaller {
        roles = Set.copyOf(roles);
    }
}
