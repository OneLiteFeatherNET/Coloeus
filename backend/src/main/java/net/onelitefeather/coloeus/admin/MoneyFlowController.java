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

/** The money flow of the staff overview under {@code /v1/staff/money-flow}. Read-only. */
@Controller("/v1/staff/money-flow")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class MoneyFlowController {

    private final MoneyFlowService flow;

    public MoneyFlowController(MoneyFlowService flow) {
        this.flow = flow;
    }

    @Secured({Roles.MODERATOR, Roles.ADMIN})
    @Get
    public StaffViews.MoneyFlow flow(@Nullable @QueryValue String window) {
        return flow.flow(MoneyFlowService.Window.parse(window));
    }

    @Secured({Roles.MODERATOR, Roles.ADMIN})
    @Get("/series")
    public StaffViews.MoneySeries series(@Nullable @QueryValue String currency, @Nullable @QueryValue Integer days) {
        return flow.series(currency, days);
    }
}
