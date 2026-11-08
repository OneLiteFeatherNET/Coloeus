package net.onelitefeather.coloeus.ledger;

import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import net.onelitefeather.coloeus.auth.Caller;
import net.onelitefeather.coloeus.auth.GrantService;
import net.onelitefeather.coloeus.currency.CurrencyService;

/**
 * Books transactions idempotently. The key row and the booking share one database transaction, so a key exists
 * exactly when its transaction exists (design decision 5).
 */
@Singleton
public class TransactionService {

    /** How long a key is kept; a retry within this period replays the stored answer. */
    static final Duration RETENTION = Duration.ofDays(7);
    static final String PATH = "/v1/transactions";

    private final GrantService grants;
    private final IdempotentWriter writer;
    private final BookingService booking;
    private final RequestHash requestHash;
    private final CurrencyService currencies;
    private final JsonMapper json;

    public TransactionService(GrantService grants, IdempotentWriter writer, BookingService booking,
                              RequestHash requestHash, CurrencyService currencies, JsonMapper json) {
        this.grants = grants;
        this.writer = writer;
        this.booking = booking;
        this.requestHash = requestHash;
        this.currencies = currencies;
        this.json = json;
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
        return writer.write(IdempotencyScope.client(caller.clientId()), caller.clientId(), key, hash, RETENTION,
                connection -> grants.requireRegistered(connection, caller),
                (connection, now) -> {
                    TransactionResponse booked = booking.book(connection, caller, command, now, metadataJson);
                    return new IdempotentWriter.Outcome(booked.id(), write(booked));
                });
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException("answer could not be written as JSON", e);
        }
    }
}
