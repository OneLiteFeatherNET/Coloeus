package net.onelitefeather.coloeus.admin;

import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import net.onelitefeather.coloeus.ledger.IdempotencyScope;
import net.onelitefeather.coloeus.ledger.IdempotentWriter;
import net.onelitefeather.coloeus.ledger.RequestHash;
import net.onelitefeather.coloeus.persistence.SqlStep;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;
import net.onelitefeather.coloeus.request.RequestValues;

/**
 * The shared frame of every staff write: the idempotency key of the caller's scope ({@code staff:<oid>}), the
 * validation of the reason, and the JSON of the answer. The write, its audit row and the key row share one database
 * transaction ({@link IdempotentWriter}).
 */
@Singleton
public class StaffWrites {

    /** How long a staff key is kept; a retry within this period replays the stored answer. */
    static final Duration RETENTION = Duration.ofDays(7);

    static final int MAX_REASON_LENGTH = 255;

    private final IdempotentWriter writer;
    private final JsonMapper json;
    private final RequestHash requestHash;

    public StaffWrites(IdempotentWriter writer, JsonMapper json, RequestHash requestHash) {
        this.writer = writer;
        this.json = json;
        this.requestHash = requestHash;
    }

    /** The body as a JSON object; a missing or non-object body is refused. */
    public JsonNode tree(String body) {
        return RequestValues.jsonObject(json, body);
    }

    /** The hash of a write's method, path and canonical body (the same rule as the client writes). */
    public String hash(String method, String path, JsonNode node) {
        return requestHash.of(method, path, node);
    }

    /**
     * Binds the body to its record. A body that does not match the record's fields is refused.
     *
     * @throws ProblemException {@link ProblemCode#REQUEST_INVALID}
     */
    public <T> T bind(String body, Class<T> type) {
        try {
            return json.readValue(body, type);
        } catch (IOException e) {
            throw RequestValues.invalid("the request body does not match the expected fields");
        }
    }

    /** Runs a staff write once per key and returns the JSON answer; a repeat returns the stored answer. */
    public String run(StaffActor actor, String key, String requestHash, IdempotentWriter.Work work) {
        SqlStep noGuard = connection -> {
        };
        return writer.write(IdempotencyScope.staff(actor.oid()), null, key, requestHash, RETENTION, noGuard, work);
    }

    /** The JSON of an answer. */
    public String json(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException("the answer could not be written as JSON", e);
        }
    }

    /**
     * The reason of a money write: 1 to 255 characters, not blank after trimming. Stored trimmed.
     *
     * @throws ProblemException {@link ProblemCode#REQUEST_INVALID}
     */
    static String reason(String value) {
        if (value == null) {
            throw RequestValues.invalid("reason is required");
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            throw RequestValues.invalid("reason must not be blank");
        }
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_REASON_LENGTH) {
            throw RequestValues.invalid("reason must not be longer than " + MAX_REASON_LENGTH + " characters");
        }
        return trimmed;
    }
}
