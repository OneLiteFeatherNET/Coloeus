package net.onelitefeather.coloeus.ledger;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import net.onelitefeather.coloeus.auth.Caller;
import net.onelitefeather.coloeus.auth.CurrentCaller;
import net.onelitefeather.coloeus.auth.Roles;
import net.onelitefeather.coloeus.request.RequestValues;

/**
 * Transaction endpoint under {@code /v1/transactions}. The caller is the verified token; the rules of who may book
 * what are in {@link BookingService}. The route blocks on JDBC, so it runs on the blocking executor.
 */
@Controller("/v1/transactions")
@ExecuteOn(TaskExecutors.BLOCKING)
public class TransactionController {

    private final TransactionService service;
    private final CurrentCaller callers;
    private final JsonMapper json;

    public TransactionController(TransactionService service, CurrentCaller callers, JsonMapper json) {
        this.service = service;
        this.callers = callers;
        this.json = json;
    }

    @Post
    @Secured({Roles.CLIENT, Roles.ADMIN})
    public HttpResponse<String> post(@Nullable @Header(RequestValues.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
                                     @Nullable @Body String body) {
        Caller caller = callers.get();
        String key = RequestValues.idempotencyKey(idempotencyKey);
        JsonNode node = RequestValues.jsonObject(json, body);
        return HttpResponse.<String>status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .body(service.post(caller, key, node));
    }
}
