package net.onelitefeather.coloeus.ledger;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import java.time.Clock;
import java.time.Instant;
import net.onelitefeather.coloeus.persistence.JdbcTransactions;

/**
 * Deletes expired idempotency keys once a day. Keys are not journal data, so this is the only delete of the
 * schema's idempotency table.
 */
@Singleton
public class IdempotencyCleanup {

    private final JdbcTransactions transactions;
    private final IdempotencyRepository idempotency;
    private final Clock clock;

    public IdempotencyCleanup(JdbcTransactions transactions, IdempotencyRepository idempotency, Clock clock) {
        this.transactions = transactions;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    /** The daily run, at 03:17 UTC. Every replica may run it; deleting the same expired rows twice is harmless. */
    @Scheduled(cron = "0 17 3 * * *", zoneId = "UTC")
    public void deleteExpiredDaily() {
        deleteExpired(clock.instant());
    }

    /** Deletes the keys that expired at or before {@code now} and returns how many were deleted. */
    public int deleteExpired(Instant now) {
        return transactions.inTransaction(connection -> idempotency.deleteExpired(connection, now));
    }
}
