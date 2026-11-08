package net.onelitefeather.coloeus.ledger;

import jakarta.inject.Singleton;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.onelitefeather.coloeus.persistence.JdbcTransactions;
import net.onelitefeather.coloeus.persistence.SqlStep;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;

/**
 * Runs a write once per {@code (scope, key)} (design decision 5). The key row, the write and its answer share one
 * database transaction, so a key exists exactly when its write exists. A repeat with the same request hash returns the
 * stored answer; a repeat with another hash is a conflict.
 */
@Singleton
public class IdempotentWriter {

    private static final int ATTEMPTS = 3;

    /** The transaction id (null when the write books nothing) and the JSON answer of a write. */
    public record Outcome(UUID transactionId, String answer) {
    }

    /** The write itself, run inside the transaction after the key row was inserted. */
    @FunctionalInterface
    public interface Work {
        Outcome apply(Connection connection, Instant now) throws SQLException;
    }

    private final JdbcTransactions transactions;
    private final IdempotencyRepository idempotency;
    private final Clock clock;

    public IdempotentWriter(JdbcTransactions transactions, IdempotencyRepository idempotency, Clock clock) {
        this.transactions = transactions;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    /**
     * @param clientId  the client of a client scope, null for a staff scope
     * @param guard     runs before the key row is inserted, for example the registry check of a client
     * @return the answer of the first write with this key, also for a repeat
     * @throws ProblemException {@link ProblemCode#IDEMPOTENCY_CONFLICT} when the key was used with another request
     */
    public String write(String scope, UUID clientId, String key, String requestHash, Duration retention,
                        SqlStep guard, Work work) {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            Instant now = clock.instant();
            try {
                return transactions.inTransaction(connection -> {
                    guard.run(connection);
                    idempotency.insertKey(connection, scope, clientId, key, requestHash, now.plus(retention));
                    Outcome outcome = work.apply(connection, now);
                    idempotency.complete(connection, scope, key, outcome.transactionId(), outcome.answer());
                    // Answer with the stored form, so the first answer and a replay are byte-identical (jsonb
                    // normalizes the text on storage).
                    return idempotency.find(connection, scope, key).map(IdempotencyRepository.StoredKey::response)
                            .orElse(outcome.answer());
                });
            } catch (IdempotencyKeyTaken taken) {
                Optional<IdempotencyRepository.StoredKey> stored =
                        transactions.read(connection -> idempotency.find(connection, scope, key));
                if (stored.isEmpty()) {
                    // The first write rolled back after the key was taken; this attempt may write now.
                    continue;
                }
                if (!stored.get().requestHash().equals(requestHash)) {
                    throw ProblemException.of(ProblemCode.IDEMPOTENCY_CONFLICT,
                            "the idempotency key " + key + " was used with another request");
                }
                return stored.get().response();
            }
        }
        throw new IllegalStateException("idempotency key " + key + " has neither a write nor a stored answer");
    }
}
