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
import net.onelitefeather.coloeus.currency.CreateCurrencyRequest;
import net.onelitefeather.coloeus.currency.CurrencyService;
import net.onelitefeather.coloeus.request.RequestValues;

/**
 * The currencies of the staff API under {@code /v1/staff/currencies}. Reads for moderators and admins; creation and
 * the default for admins.
 */
@Controller("/v1/staff/currencies")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class StaffCurrencyController {

    private final CurrencyService currencies;
    private final StaffConfigService config;
    private final StaffWrites writes;

    public StaffCurrencyController(CurrencyService currencies, StaffConfigService config, StaffWrites writes) {
        this.currencies = currencies;
        this.config = config;
        this.writes = writes;
    }

    @Secured({Roles.MODERATOR, Roles.ADMIN})
    @Get
    public StaffViews.CurrenciesResponse list() {
        return new StaffViews.CurrenciesResponse(currencies.list());
    }

    @Secured(Roles.ADMIN)
    @Post
    public HttpResponse<String> create(HttpRequest<?> request,
                                       @Nullable @Header(RequestValues.IDEMPOTENCY_KEY_HEADER) String key,
                                       @Nullable @Body String body) {
        String idempotencyKey = RequestValues.idempotencyKey(key);
        JsonNode node = writes.tree(body);
        String hash = writes.hash(request.getMethod().name(), request.getPath(), node);
        CreateCurrencyRequest create = writes.bind(body, CreateCurrencyRequest.class);
        return StaffResponses.json(HttpStatus.CREATED,
                config.createCurrency(StaffRequestFilter.actorOf(request), create, idempotencyKey, hash));
    }

    @Secured(Roles.ADMIN)
    @Put("/{code}/default")
    public HttpResponse<String> makeDefault(HttpRequest<?> request, @PathVariable String code,
                                            @Nullable @Header(RequestValues.IDEMPOTENCY_KEY_HEADER) String key) {
        String idempotencyKey = RequestValues.idempotencyKey(key);
        String hash = writes.hash(request.getMethod().name(), request.getPath(),
                JsonNode.createObjectNode(new java.util.LinkedHashMap<>()));
        return StaffResponses.json(HttpStatus.OK,
                config.makeDefault(StaffRequestFilter.actorOf(request), code, idempotencyKey, hash));
    }
}
