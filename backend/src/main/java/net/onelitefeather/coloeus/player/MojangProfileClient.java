package net.onelitefeather.coloeus.player;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.client.annotation.Client;

/**
 * The Mojang profile API ({@code micronaut.http.services.mojang}: base URL from {@code coloeus.mojang.url}, read timeout
 * 2 s). Answers 200 with the profile, 204 or 404 for an unknown name, anything else is a failure.
 */
@Client(id = "mojang")
public interface MojangProfileClient {

    @Get("/users/profiles/minecraft/{name}")
    HttpResponse<MojangProfile> profile(@PathVariable String name);
}
