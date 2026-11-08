package net.onelitefeather.coloeus.account;

import static net.onelitefeather.coloeus.support.ProblemAssertions.assertProblem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.onelitefeather.coloeus.currency.CreateCurrencyRequest;
import net.onelitefeather.coloeus.currency.CurrencyService;
import net.onelitefeather.coloeus.money.AmountJson;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.support.LedgerApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@Tag("integration")
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountApiIntegrationTest extends LedgerApiIntegrationTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Inject
    CurrencyService currencies;

    @BeforeEach
    void defineCurrencies() {
        currencies.create(new CreateCurrencyRequest("COINS", "Coin", "Coins", "C", 2));
        currencies.create(new CreateCurrencyRequest("GEMS", "Gem", "Gems", "G", 0));
    }

    @Test
    void unseenPlayerGetsAZeroBalanceInEveryCurrency() {
        PlayerAccountResponse account = player(PLAYER);

        assertEquals("PLAYER", account.kind(), "a player account must have the kind PLAYER");
        assertEquals(PLAYER, account.owner(), "the owner must be the player UUID");
        assertEquals(List.of(new AmountJson("0.00", "COINS"), new AmountJson("0", "GEMS")), account.balances(),
                "every currency must be listed with zero and its own fraction digits, ordered by code");
    }

    @Test
    void readingThePlayerAccountTwiceReturnsTheSameAccount() {
        UUID first = player(PLAYER).id();

        UUID second = player(PLAYER).id();

        assertEquals(first, second, "a second read must return the account created by the first");
    }

    @Test
    void concurrentFirstAccessCreatesExactlyOneAccount() throws Exception {
        int callers = 16;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch startGate = new CountDownLatch(1);
        List<UUID> ids = new ArrayList<>();
        try {
            List<Future<UUID>> answers = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                answers.add(pool.submit(() -> {
                    startGate.await();
                    return player(PLAYER).id();
                }));
            }
            startGate.countDown();
            for (Future<UUID> answer : answers) {
                ids.add(answer.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, ids.stream().distinct().count(), "all concurrent reads must see one account id");
        assertEquals(1L, accountCountOf(PLAYER), "exactly one account may exist for the player UUID");
    }

    @Test
    void balancesOfANewAccountHaveNoLatestPosting() {
        UUID id = player(PLAYER).id();

        BalancesResponse balances = client.toBlocking().retrieve(
                get(TOKENS.client(), "/v1/accounts/" + id + "/balances"), BalancesResponse.class);

        assertEquals(id, balances.accountId(), "the balances must belong to the requested account");
        assertNull(balances.latestPostingId(), "an account without postings has no latest posting");
        assertEquals(2, balances.balances().size(), "every defined currency must be listed");
    }

    @Test
    void balancesOfAnUnknownAccountAnswer404AccountNotFound() {
        HttpClientResponseException problem = assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().exchange(get(TOKENS.client(), "/v1/accounts/" + UUID.randomUUID() + "/balances")),
                "an unknown account must not be answered with a balance");

        assertProblem(problem, HttpStatus.NOT_FOUND, ProblemCode.ACCOUNT_NOT_FOUND);
    }

    private PlayerAccountResponse player(UUID uuid) {
        return client.toBlocking().retrieve(get(TOKENS.client(), "/v1/accounts/player/" + uuid),
                PlayerAccountResponse.class);
    }

    private static long accountCountOf(UUID owner) {
        return query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT count(*) FROM account WHERE owner_uuid = ?")) {
                statement.setObject(1, owner);
                try (ResultSet rows = statement.executeQuery()) {
                    rows.next();
                    return rows.getLong(1);
                }
            }
        });
    }
}
