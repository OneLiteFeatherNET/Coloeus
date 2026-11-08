package net.onelitefeather.coloeus.player;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import net.onelitefeather.coloeus.auth.Roles;

/**
 * Player names under {@code /v1/players}: joins are reported by clients, lookups are answered to clients and staff.
 * The routes block on JDBC and on the Mojang client, so they run on the blocking executor, not on the event loop.
 */
@Controller("/v1/players")
@ExecuteOn(TaskExecutors.BLOCKING)
public class PlayerNameController {

    private final PlayerNameService service;

    public PlayerNameController(PlayerNameService service) {
        this.service = service;
    }

    @Put("/{uuid}/name")
    @Secured(Roles.CLIENT)
    public PlayerNameView recordJoin(@PathVariable String uuid, @Body PlayerNameRequest request) {
        return service.recordJoin(uuid, request == null ? null : request.name());
    }

    @Get("/by-name/{name}")
    @Secured({Roles.CLIENT, Roles.ADMIN, Roles.MODERATOR})
    public PlayerLookup byName(@PathVariable String name) {
        return service.lookup(name);
    }
}
