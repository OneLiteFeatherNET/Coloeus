package net.onelitefeather.coloeus.admin;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

/**
 * A route under {@code /v1/staff} that only answers, so the gate can be tested without the real staff routes. It has no
 * role rule of its own: the gate under test is the only one that decides.
 */
@Controller("/v1/staff/probe")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class StaffProbeController {

    @Get
    public String read() {
        return "ok";
    }

    @Post
    public String write(@Nullable @Body String body) {
        return "ok";
    }
}
