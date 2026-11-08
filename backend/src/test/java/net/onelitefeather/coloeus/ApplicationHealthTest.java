package net.onelitefeather.coloeus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
class ApplicationHealthTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void healthEndpointAnswersOk() {
        var response = client.toBlocking().exchange(HttpRequest.GET("/health"));

        assertEquals(HttpStatus.OK, response.status(), "GET /health should answer 200 OK");
    }
}
