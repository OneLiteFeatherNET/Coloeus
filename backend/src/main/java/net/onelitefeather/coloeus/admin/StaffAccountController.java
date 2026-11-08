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
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import net.onelitefeather.coloeus.auth.Roles;
import java.util.UUID;
import net.onelitefeather.coloeus.request.RequestValues;

/**
 * Player accounts of the staff API under {@code /v1/staff/accounts}. Reads for moderators and admins; reset and
 * adjust for admins (the gate refuses a moderator's write before the body is read).
 */
@Controller("/v1/staff/accounts")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class StaffAccountController {

    private final StaffAccountService accounts;
    private final StaffMoneyService money;
    private final StaffWrites writes;

    public StaffAccountController(StaffAccountService accounts, StaffMoneyService money, StaffWrites writes) {
        this.accounts = accounts;
        this.money = money;
        this.writes = writes;
    }

    @Secured({Roles.MODERATOR, Roles.ADMIN})
    @Get("/search")
    public StaffViews.AccountHits search(@Nullable @QueryValue String name, @Nullable @QueryValue String uuid) {
        return accounts.search(name, uuid);
    }

    @Secured({Roles.MODERATOR, Roles.ADMIN})
    @Get("/{id}")
    public StaffViews.AccountDetail detail(@PathVariable UUID id) {
        return accounts.detail(id);
    }

    @Secured({Roles.MODERATOR, Roles.ADMIN})
    @Get("/{id}/postings")
    public StaffViews.StaffPostingsPage postings(@PathVariable UUID id, @Nullable @QueryValue Integer limit,
                                                 @Nullable @QueryValue String cursor) {
        return accounts.postings(id, limit, cursor);
    }

    @Secured(Roles.ADMIN)
    @Post("/{id}/reset")
    public HttpResponse<String> reset(HttpRequest<?> request, @PathVariable UUID id,
                                      @Nullable @Header(RequestValues.IDEMPOTENCY_KEY_HEADER) String key,
                                      @Nullable @Body String body) {
        String idempotencyKey = RequestValues.idempotencyKey(key);
        JsonNode node = writes.tree(body);
        String hash = writes.hash(request.getMethod().name(), request.getPath(), node);
        StaffBodies.Reset reset = writes.bind(body, StaffBodies.Reset.class);
        return StaffResponses.json(HttpStatus.CREATED,
                money.reset(StaffRequestFilter.actorOf(request), id, reset, idempotencyKey, hash));
    }

    @Secured(Roles.ADMIN)
    @Post("/{id}/adjust")
    public HttpResponse<String> adjust(HttpRequest<?> request, @PathVariable UUID id,
                                       @Nullable @Header(RequestValues.IDEMPOTENCY_KEY_HEADER) String key,
                                       @Nullable @Body String body) {
        String idempotencyKey = RequestValues.idempotencyKey(key);
        JsonNode node = writes.tree(body);
        String hash = writes.hash(request.getMethod().name(), request.getPath(), node);
        StaffBodies.Adjust adjust = writes.bind(body, StaffBodies.Adjust.class);
        return StaffResponses.json(HttpStatus.CREATED,
                money.adjust(StaffRequestFilter.actorOf(request), id, adjust, idempotencyKey, hash));
    }
}
