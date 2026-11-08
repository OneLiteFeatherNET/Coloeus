package net.onelitefeather.coloeus.request;

import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;

/**
 * Checks of raw request values. Every failure is a {@link ProblemException}, so clients get one answer format.
 */
public final class RequestValues {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;
    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private RequestValues() {
    }

    public static ProblemException invalid(String detail) {
        return ProblemException.of(ProblemCode.REQUEST_INVALID, detail);
    }

    /** A UUID in canonical text form. */
    public static UUID uuid(String value, String field) {
        if (value == null || !UUID_TEXT.matcher(value).matches()) {
            throw invalid(field + " must be a UUID");
        }
        return UUID.fromString(value);
    }

    /** The Idempotency-Key header of a write; missing or blank keys are rejected before the body is read. */
    public static String idempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw ProblemException.of(ProblemCode.IDEMPOTENCY_KEY_MISSING,
                    "the " + IDEMPOTENCY_KEY_HEADER + " header is required on this request");
        }
        if (value.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw invalid("the " + IDEMPOTENCY_KEY_HEADER + " header must not be longer than "
                    + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        return value;
    }

    /** Parses a request body that must be one JSON object. */
    public static JsonNode jsonObject(JsonMapper mapper, String body) {
        if (body == null || body.isBlank()) {
            throw invalid("the request body is required");
        }
        JsonNode node;
        try {
            node = mapper.readValue(body, JsonNode.class);
        } catch (IOException e) {
            throw invalid("the request body is not valid JSON");
        }
        if (node == null || !node.isObject()) {
            throw invalid("the request body must be a JSON object");
        }
        return node;
    }
}
