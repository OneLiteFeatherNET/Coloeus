package net.onelitefeather.coloeus.admin;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import net.onelitefeather.coloeus.auth.Roles;

/** The staff action log under {@code /v1/staff/staff-actions}, newest first. Reads for moderators and admins. */
@Controller("/v1/staff/staff-actions")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class StaffActionController {

    private final StaffActionService actions;

    public StaffActionController(StaffActionService actions) {
        this.actions = actions;
    }

    @Secured({Roles.MODERATOR, Roles.ADMIN})
    @Get
    public StaffViews.StaffActionsPage list(@Nullable @QueryValue Integer limit, @Nullable @QueryValue String cursor) {
        return actions.list(limit, cursor);
    }
}
