package net.onelitefeather.coloeus.currency;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.HttpStatus;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import net.onelitefeather.coloeus.auth.Roles;

import java.util.List;

/**
 * Currency endpoints under {@code /v1/currencies}. Reads are open to every app role; writes are for admins. The routes
 * block on JDBC, so they run on the blocking executor.
 */
@Controller("/v1/currencies")
@ExecuteOn(TaskExecutors.BLOCKING)
public class CurrencyController {

    private final CurrencyService service;

    public CurrencyController(CurrencyService service) {
        this.service = service;
    }

    @Post
    @Status(HttpStatus.CREATED)
    @Secured(Roles.ADMIN)
    public Currency create(@Body CreateCurrencyRequest request) {
        return service.create(request);
    }

    @Get
    @Secured({Roles.CLIENT, Roles.ADMIN, Roles.MODERATOR})
    public List<Currency> list() {
        return service.list();
    }

    @Put("/{code}")
    @Secured(Roles.ADMIN)
    public Currency update(@PathVariable String code, @Body UpdateCurrencyRequest request) {
        return service.update(code, request);
    }

    @Put("/{code}/default")
    @Secured(Roles.ADMIN)
    public Currency makeDefault(@PathVariable String code) {
        return service.makeDefault(code);
    }
}
