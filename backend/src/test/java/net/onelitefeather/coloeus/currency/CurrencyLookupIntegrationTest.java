package net.onelitefeather.coloeus.currency;

import static net.onelitefeather.coloeus.support.ProblemAssertions.assertProblem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;
import net.onelitefeather.coloeus.auth.Permission;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.support.LedgerApiIntegrationTest;
import net.onelitefeather.coloeus.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * A currency another replica created is not in this replica's JSR 354 provider yet. The lookup reloads the provider
 * once on a miss, so the currency resolves here without a restart.
 */
@Tag("integration")
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CurrencyLookupIntegrationTest extends LedgerApiIntegrationTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Inject
    CurrencyService currencies;

    @BeforeEach
    void serveOnlyCoinsHere() {
        // Creating COINS reloads this replica's provider from the (empty) database: GEMS is unknown to it.
        currencies.create(new CreateCurrencyRequest("COINS", "Coin", "Coins", "C", 2));
        registerClient(TestTokens.CLIENT_APP);
    }

    @Test
    void currencyCreatedElsewhereResolvesAfterOneReload() throws SQLException {
        insertCurrencyOfAnotherReplica("GEMS", 0);

        assertTrue(currencies.lookup("GEMS").isPresent(),
                "a currency stored by another replica must resolve on this replica after the reload");
    }

    @Test
    void mintInCurrencyCreatedElsewhereIsBooked() throws SQLException {
        insertCurrencyOfAnotherReplica("GEMS", 0);
        grant(TestTokens.CLIENT_APP, "GEMS", Permission.MINT);

        var response = client.toBlocking().exchange(HttpRequest.POST("/v1/transactions",
                        "{\"kind\":\"MINT\",\"to\":{\"player\":\"" + ALICE + "\"},"
                                + "\"amount\":{\"amount\":\"3\",\"currency\":\"GEMS\"}}")
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .bearerAuth(TOKENS.client())
                .header("Idempotency-Key", "gems-mint"));

        assertEquals(HttpStatus.CREATED, response.status(),
                "a mint in a currency stored by another replica must be booked, not answered CURRENCY_UNKNOWN");
    }

    @Test
    void unknownCurrencyStaysUnknownAfterTheReload() {
        assertFalse(currencies.lookup("NOPE").isPresent(), "a code that no replica defines must stay unknown");
    }

    @Test
    void mintInUnknownCurrencyAnswers422CurrencyUnknown() {
        HttpClientResponseException problem = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.POST("/v1/transactions",
                                "{\"kind\":\"MINT\",\"to\":{\"player\":\"" + ALICE + "\"},"
                                        + "\"amount\":{\"amount\":\"3\",\"currency\":\"NOPE\"}}")
                        .contentType(MediaType.APPLICATION_JSON_TYPE)
                        .bearerAuth(TOKENS.client())
                        .header("Idempotency-Key", "nope-mint")),
                "a currency that no replica defines must be refused");

        assertProblem(problem, HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.CURRENCY_UNKNOWN);
    }

    /** Writes a currency row the way another replica would: directly into the database. */
    private void insertCurrencyOfAnotherReplica(String code, int fractionDigits) throws SQLException {
        Timestamp now = Timestamp.from(clock.instant());
        query(connection -> {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO currency (id, code, name_singular, name_plural, symbol, fraction_digits, "
                            + "is_default, created_at, updated_at) VALUES (?, ?, 'Gem', 'Gems', 'G', ?, FALSE, ?, ?)")) {
                insert.setObject(1, UUID.randomUUID());
                insert.setString(2, code);
                insert.setShort(3, (short) fractionDigits);
                insert.setTimestamp(4, now);
                insert.setTimestamp(5, now);
                return insert.executeUpdate();
            }
        });
    }
}
