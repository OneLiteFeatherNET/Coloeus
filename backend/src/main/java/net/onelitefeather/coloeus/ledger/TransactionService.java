package net.onelitefeather.coloeus.ledger;

import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.onelitefeather.coloeus.auth.Caller;
import net.onelitefeather.coloeus.auth.GrantService;
import net.onelitefeather.coloeus.currency.CurrencyService;
import net.onelitefeather.coloeus.persistence.JdbcTransactions;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;

/**
 * Books transactions idempotently. The key row and the booking share one database transaction, so a key exists
 * exactly when its transaction exists (design decision 5).
 */
@Singleton
public class TransactionService {

    /** How long a key is kept; a retry within this period replays the stored answer. */
    static final Duration RETENTION = Duration.ofDays(7);
    static final String PATH = "/v1/transactions";

    private static final int ATTEMPTS = 3;

    private final JdbcTransactions transactions;
    private final GrantService grants;
    private final IdempotencyRepository idempotency;
    private final BookingService booking;
    private final RequestHash requestHash;
    private final CurrencyService currencies;
    private final JsonMapper json;
    private final Clock clock;

    public TransactionService(JdbcTransactions transactions, GrantService grants,
                              IdempotencyRepository idempotency, BookingService booking, RequestHash requestHash,
                              CurrencyService currencies, JsonMapper json, Clock clock) {
        this.transactions = transactions;
        this.grants = grants;
        this.idempotency = idempotency;
        this.booking = booking;
        this.requestHash = requestHash;
        this.currencies = currencies;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Books the transaction of a body once per {@code (client, key)} and returns the JSON answer. A repeat with the
     * same body returns the stored answer; a repeat with another body is a conflict. An unregistered client is
     * refused before anything is stored.
     */
    public String post(Caller caller, String key, JsonNode body) {
        TransactionCommand command = TransactionRequestParser.parse(body, currencies::lookup);
        String hash = requestHash.of("POST", PATH, body);
        String metadataJson = write(command.metadata());
        UUID clientId = caller.clientId();
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            Instant now = clock.instant();
            try {
                return transactions.inTransaction(connection -> {
                    // The key row references the client row, so an unregistered client is refused before the key.
                    grants.requireRegistered(connection, caller);
                    idempotency.insertKey(connection, clientId, key, hash, now.plus(RETENTION));
                    TransactionResponse booked = booking.book(connection, caller, command, now, metadataJson);
                    String answer = write(booked);
                    idempotency.complete(connection, clientId, key, booked.id(), answer);
                    return answer;
                });
            } catch (IdempotencyKeyTaken taken) {
                Optional<IdempotencyRepository.StoredKey> stored =
                        transactions.read(connection -> idempotency.find(connection, clientId, key));
                if (stored.isEmpty()) {
                    // The first booking rolled back after the key was taken; this attempt may book now.
                    continue;
                }
                if (!stored.get().requestHash().equals(hash)) {
                    throw ProblemException.of(ProblemCode.IDEMPOTENCY_CONFLICT,
                            "the idempotency key " + key + " was used with another request");
                }
                return stored.get().response();
            }
        }
        throw new IllegalStateException("idempotency key " + key + " has neither a booking nor a stored answer");
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException("answer could not be written as JSON", e);
        }
    }
}
