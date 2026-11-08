package net.onelitefeather.coloeus.account;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import java.util.UUID;
import net.onelitefeather.coloeus.auth.CurrentCaller;
import net.onelitefeather.coloeus.auth.GrantService;
import net.onelitefeather.coloeus.auth.Roles;

/**
 * Account endpoints under {@code /v1/accounts}. Readable by clients, admins and moderators; a registered client needs
 * a READ grant (see {@link GrantService#requireRead}). The routes block on JDBC, so they run on the blocking executor.
 */
@Controller("/v1/accounts")
@ExecuteOn(TaskExecutors.BLOCKING)
public class AccountController {

    private final AccountService service;
    private final GrantService grants;
    private final CurrentCaller callers;

    public AccountController(AccountService service, GrantService grants, CurrentCaller callers) {
        this.service = service;
        this.grants = grants;
        this.callers = callers;
    }

    @Get("/player/{uuid}")
    @Secured({Roles.CLIENT, Roles.ADMIN, Roles.MODERATOR})
    public PlayerAccountResponse player(@PathVariable UUID uuid) {
        grants.requireRead(callers.get());
        return service.playerAccount(uuid);
    }

    @Get("/{id}/balances")
    @Secured({Roles.CLIENT, Roles.ADMIN, Roles.MODERATOR})
    public BalancesResponse balances(@PathVariable UUID id) {
        grants.requireRead(callers.get());
        return service.balances(id);
    }

    @Get("/{id}/postings")
    @Secured({Roles.CLIENT, Roles.ADMIN, Roles.MODERATOR})
    public PostingsResponse postings(@PathVariable UUID id,
                                     @Nullable @QueryValue Integer limit,
                                     @Nullable @QueryValue String cursor) {
        grants.requireRead(callers.get());
        return service.postings(id, limit, cursor);
    }
}
