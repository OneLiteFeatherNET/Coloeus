package net.onelitefeather.coloeus.admin;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import net.onelitefeather.coloeus.auth.Roles;
import net.onelitefeather.coloeus.auth.ClientService;
import net.onelitefeather.coloeus.auth.RegisterClientRequest;
import net.onelitefeather.coloeus.request.RequestValues;

/**
 * The client registry of the staff API under {@code /v1/staff/clients}. Reads for moderators and admins; registration
 * and grants for admins.
 */
@Controller("/v1/staff/clients")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class StaffClientController {

    private final ClientService clients;
    private final StaffConfigService config;
    private final StaffWrites writes;

    public StaffClientController(ClientService clients, StaffConfigService config, StaffWrites writes) {
        this.clients = clients;
        this.config = config;
        this.writes = writes;
    }

    @Secured({Roles.MODERATOR, Roles.ADMIN})
    @Get
    public StaffViews.ClientsResponse list() {
        return new StaffViews.ClientsResponse(clients.list());
    }

    @Secured(Roles.ADMIN)
    @Post
    public HttpResponse<String> register(HttpRequest<?> request,
                                         @Nullable @Header(RequestValues.IDEMPOTENCY_KEY_HEADER) String key,
                                         @Nullable @Body String body) {
        String idempotencyKey = RequestValues.idempotencyKey(key);
        JsonNode node = writes.tree(body);
        String hash = writes.hash(request.getMethod().name(), request.getPath(), node);
        RegisterClientRequest register = writes.bind(body, RegisterClientRequest.class);
        return StaffResponses.json(HttpStatus.CREATED,
                config.registerClient(StaffRequestFilter.actorOf(request), register, idempotencyKey, hash));
    }

    @Secured(Roles.ADMIN)
    @Put("/{clientId}/grants")
    public HttpResponse<String> replaceGrants(HttpRequest<?> request, @PathVariable String clientId,
                                              @Nullable @Header(RequestValues.IDEMPOTENCY_KEY_HEADER) String key,
                                              @Nullable @Body String body) {
        String idempotencyKey = RequestValues.idempotencyKey(key);
        JsonNode node = writes.tree(body);
        String hash = writes.hash(request.getMethod().name(), request.getPath(), node);
        StaffBodies.Grants grants = writes.bind(body, StaffBodies.Grants.class);
        return StaffResponses.json(HttpStatus.OK,
                config.replaceGrants(StaffRequestFilter.actorOf(request), clientId, grants, idempotencyKey, hash));
    }
}
