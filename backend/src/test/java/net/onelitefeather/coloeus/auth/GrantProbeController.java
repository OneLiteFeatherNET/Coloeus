package net.onelitefeather.coloeus.auth;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.Status;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.utils.SecurityService;
import java.util.UUID;

/**
 * Test-only route that calls the grant checks the way a transaction endpoint will. Slice B adds the real endpoints that
 * use {@link GrantService} and {@link ActorCheck}; until then the checks are exercised through this probe.
 */
@Controller("/v1/probe")
@Requires(env = Environment.TEST)
public class GrantProbeController {

    private final GrantService grants;
    private final SecurityService security;

    public GrantProbeController(GrantService grants, SecurityService security) {
        this.grants = grants;
        this.security = security;
    }

    @Post("/grants/{currency}/{permission}")
    @Status(HttpStatus.NO_CONTENT)
    @Secured({Roles.CLIENT, Roles.ADMIN, Roles.MODERATOR})
    public void requireGrant(@PathVariable String currency, @PathVariable String permission) {
        grants.require(caller(), currency, Permission.parse(permission));
    }

    @Post("/on-behalf/{owner}")
    @Status(HttpStatus.NO_CONTENT)
    @Secured({Roles.CLIENT, Roles.ADMIN})
    public void transferOnBehalf(@PathVariable String owner, @QueryValue @Nullable String actor) {
        Caller caller = caller();
        if (!caller.admin()) {
            grants.require(caller, "COINS", Permission.TRANSFER_ON_BEHALF);
        }
        ActorCheck.require(caller, UUID.fromString(owner), actor);
    }

    /** Has no security rule, so the default must refuse it. */
    @Get("/unannotated")
    public String unannotated() {
        return "reached";
    }

    private Caller caller() {
        return Caller.from(security.getAuthentication().orElseThrow());
    }
}
