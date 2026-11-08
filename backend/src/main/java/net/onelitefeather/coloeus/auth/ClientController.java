package net.onelitefeather.coloeus.auth;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Status;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;

import java.util.List;

/**
 * The client registry under {@code /v1/clients}. Admin only. The routes block on JDBC, so they run on the blocking
 * executor, not on the event loop.
 */
@Controller("/v1/clients")
@ExecuteOn(TaskExecutors.BLOCKING)
public class ClientController {

    private final ClientService service;

    public ClientController(ClientService service) {
        this.service = service;
    }

    @Post
    @Status(HttpStatus.CREATED)
    @Secured(Roles.ADMIN)
    public ClientView register(@Body RegisterClientRequest request) {
        return service.register(request);
    }

    @Get
    @Secured(Roles.ADMIN)
    public List<ClientView> list() {
        return service.list();
    }

    @Put("/{id}/grants")
    @Secured(Roles.ADMIN)
    public List<GrantView> replaceGrants(@PathVariable String id, @Body List<GrantRequest> grants) {
        return service.replaceGrants(id, grants);
    }
}
