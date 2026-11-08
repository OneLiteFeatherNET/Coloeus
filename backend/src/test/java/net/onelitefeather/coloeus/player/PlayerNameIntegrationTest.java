package net.onelitefeather.coloeus.player;

import static net.onelitefeather.coloeus.support.Api.assertProblem;
import static net.onelitefeather.coloeus.support.Api.bearer;
import static net.onelitefeather.coloeus.support.Api.refusal;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.support.FakeMojang;
import net.onelitefeather.coloeus.support.MutableClock;
import net.onelitefeather.coloeus.support.PostgresTestContainer;
import net.onelitefeather.coloeus.support.TestClockFactory;
import net.onelitefeather.coloeus.support.TestDatabase;
import net.onelitefeather.coloeus.support.TestTokens;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Player names (design decision 8): joins are recorded, lookups answer from the register and fall back to the Mojang
 * profile API. The Mojang API is a local fake; its read timeout is set short so the timeout test runs at once.
 */
@Tag("integration")
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlayerNameIntegrationTest implements TestPropertyProvider {

    private static final PostgreSQLContainer POSTGRES = PostgresTestContainer.start();
    private static final TestTokens TOKENS = TestTokens.start();
    private static final FakeMojang MOJANG = FakeMojang.start();

    private static final UUID ALEX = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID OTHER = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final String STEVE_UNDASHED = "f7c77d999f154a66a87dc4a51ef30d19";
    private static final UUID STEVE = UUID.fromString("f7c77d99-9f15-4a66-a87d-c4a51ef30d19");

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    MutableClock clock;

    @Inject
    MojangProfileClient mojang;

    @Override
    public Map<String, String> getProperties() {
        Map<String, String> properties = new HashMap<>(TestDatabase.properties(POSTGRES, TOKENS));
        properties.put("coloeus.mojang.url", MOJANG.baseUrl());
        properties.put("micronaut.http.services.mojang.read-timeout", "500ms");
        return properties;
    }

    /**
     * The first call of the declarative client builds its connection and takes longer than the short read timeout of
     * this test. Warming it up here keeps that start-up cost out of the lookups the tests measure.
     */
    @BeforeAll
    void warmUpTheMojangClient() {
        try {
            mojang.profile("warmup");
        } catch (HttpClientException expected) {
            // Only the connection matters; the answer does not.
        }
    }

    @AfterAll
    static void stopFakes() {
        MOJANG.close();
        TOKENS.close();
    }

    @BeforeEach
    void emptyRegister() {
        TestDatabase.clearRegistry(POSTGRES);
        MOJANG.reset();
        clock.set(TestClockFactory.START);
    }

    @Test
    void aReportedJoinIsFoundByName() {
        join(ALEX, "Alex");

        PlayerLookup found = lookup("Alex");

        assertEquals(ALEX, found.uuid(), "the name must resolve to the UUID that joined with it");
        assertEquals(NameSource.JOIN, found.source(), "a name from a join is stored with source JOIN");
    }

    @Test
    void lookupIgnoresTheCaseOfTheName() {
        join(ALEX, "Alex");

        PlayerLookup found = lookup("aLEX");

        assertEquals(ALEX, found.uuid(), "names are matched case-insensitively");
        assertEquals("Alex", found.name(), "the answer carries the name as it was reported");
    }

    @Test
    void aRenamedPlayerKeepsTheOldNameInHistory() {
        join(ALEX, "Alex");
        clock.advance(Duration.ofMinutes(5));
        join(ALEX, "Alexa");

        PlayerLookup found = lookup("alexa");

        assertEquals(ALEX, found.uuid(), "the new name resolves to the renamed player");
        assertEquals(List.of("Alexa", "Alex"), found.history().stream().map(NameEntry::name).toList(),
                "the history of the player lists the names it held, most recent first");
    }

    @Test
    void aNameMovedToAnotherUuidResolvesToTheNewestHolder() {
        join(ALEX, "Alex");
        clock.advance(Duration.ofMinutes(5));
        join(OTHER, "Alex");

        PlayerLookup found = lookup("Alex");

        assertEquals(OTHER, found.uuid(), "the most recent holder of the name answers");
    }

    @Test
    void aNameHeldAgainByTheFirstUuidResolvesToIt() {
        join(ALEX, "Alex");
        clock.advance(Duration.ofMinutes(5));
        join(OTHER, "Alex");
        clock.advance(Duration.ofMinutes(5));
        join(ALEX, "Alex");

        assertEquals(ALEX, lookup("Alex").uuid(), "the holder with the newest last-seen time answers");
    }

    @Test
    void aNameTheRegisterDoesNotKnowIsLookedUpAtMojangAndStored() {
        MOJANG.answer("Steve42", 200, "{\"id\":\"" + STEVE_UNDASHED + "\",\"name\":\"Steve42\"}");

        PlayerLookup found = lookup("steve42");

        assertEquals(STEVE, found.uuid(), "the undashed Mojang id must be read as the dashed UUID");
        assertEquals(NameSource.MOJANG, found.source(), "an answer of Mojang is stored with source MOJANG");
        lookup("STEVE42");
        assertEquals(1, MOJANG.requestsFor("steve42"),
                "the second lookup must be answered from the register, without asking Mojang again");
    }

    @Test
    void aNameUnknownToMojangAnswers404PlayerUnknown() {
        MOJANG.answer("Nobody", 404, "{\"path\":\"/users/profiles/minecraft/Nobody\",\"errorMessage\":\"not found\"}");

        var failure = refusal(client, get("Nobody"));

        assertProblem(failure, HttpStatus.NOT_FOUND, ProblemCode.PLAYER_UNKNOWN);
    }

    @Test
    void aNoContentAnswerOfMojangAlsoAnswers404PlayerUnknown() {
        MOJANG.answer("Ghost", 204, "");

        var failure = refusal(client, get("Ghost"));

        assertProblem(failure, HttpStatus.NOT_FOUND, ProblemCode.PLAYER_UNKNOWN);
    }

    @Test
    void aMojangFailureAnswers503AndStoresNothing() {
        MOJANG.answer("Broken", 500, "");

        var failure = refusal(client, get("Broken"));

        assertProblem(failure, HttpStatus.SERVICE_UNAVAILABLE, ProblemCode.NAME_LOOKUP_UNAVAILABLE);
        MOJANG.answer("Broken", 200, "{\"id\":\"" + STEVE_UNDASHED + "\",\"name\":\"Broken\"}");
        assertEquals(STEVE, lookup("Broken").uuid(),
                "after the failure nothing was stored, so the next lookup asks Mojang again and gets its answer");
    }

    @Test
    void aMojangTimeoutAnswers503NameLookupUnavailable() {
        MOJANG.neverAnswer("Slowpoke");

        var failure = refusal(client, get("Slowpoke"));

        assertProblem(failure, HttpStatus.SERVICE_UNAVAILABLE, ProblemCode.NAME_LOOKUP_UNAVAILABLE);
    }

    @Test
    void anInvalidNameAnswers400BeforeAskingMojang() {
        var failure = refusal(client, get("not%20valid%21"));

        assertProblem(failure, HttpStatus.BAD_REQUEST, ProblemCode.REQUEST_INVALID);
        assertEquals(0, MOJANG.requestsFor("not%20valid%21"), "an invalid name must never reach Mojang, encoded or not");
    }

    @Test
    void aJoinWithAnInvalidUuidAnswers400() {
        var failure = refusal(client, bearer(
                HttpRequest.PUT("/v1/players/not-a-uuid/name", new PlayerNameRequest("Alex")), TOKENS.client()));

        assertProblem(failure, HttpStatus.BAD_REQUEST, ProblemCode.REQUEST_INVALID);
    }

    @Test
    void aModeratorMayLookUpButNotReportJoins() {
        join(ALEX, "Alex");

        PlayerLookup found = client.toBlocking().retrieve(
                bearer(HttpRequest.GET("/v1/players/by-name/Alex"), TOKENS.moderator()), PlayerLookup.class);
        var failure = refusal(client, bearer(
                HttpRequest.PUT("/v1/players/" + OTHER + "/name", new PlayerNameRequest("Other")), TOKENS.moderator()));

        assertEquals(ALEX, found.uuid(), "a moderator may look up names");
        assertProblem(failure, HttpStatus.FORBIDDEN, ProblemCode.FORBIDDEN_ROLE);
    }

    private void join(UUID uuid, String name) {
        client.toBlocking().exchange(bearer(
                HttpRequest.PUT("/v1/players/" + uuid + "/name", new PlayerNameRequest(name)), TOKENS.client()));
    }

    private PlayerLookup lookup(String name) {
        return client.toBlocking().retrieve(get(name), PlayerLookup.class);
    }

    private HttpRequest<?> get(String name) {
        return bearer(HttpRequest.GET("/v1/players/by-name/" + name), TOKENS.client());
    }
}
