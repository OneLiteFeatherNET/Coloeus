package net.onelitefeather.coloeus.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class RequestHashTest {

    private final RequestHash hash = new RequestHash(JsonMapper.createDefault());

    @Test
    void sameBodyWithAnotherKeyOrderHasTheSameHash() throws IOException {
        JsonNode ordered = json("{\"kind\":\"BURN\",\"from\":\"a\",\"amount\":\"1\"}");
        JsonNode shuffled = json("{\"amount\":\"1\",\"from\":\"a\",\"kind\":\"BURN\"}");

        assertEquals(hash.of("POST", "/v1/transactions", ordered), hash.of("POST", "/v1/transactions", shuffled),
                "the canonical form must not depend on the order of the keys");
    }

    @Test
    void anotherAmountChangesTheHash() throws IOException {
        JsonNode five = json("{\"kind\":\"BURN\",\"amount\":\"5.00\"}");
        JsonNode six = json("{\"kind\":\"BURN\",\"amount\":\"6.00\"}");

        assertNotEquals(hash.of("POST", "/v1/transactions", five), hash.of("POST", "/v1/transactions", six),
                "a different amount is a different request");
    }

    @Test
    void theHashCoversTheMethodAndThePath() throws IOException {
        JsonNode body = json("{\"kind\":\"BURN\"}");

        assertNotEquals(hash.of("POST", "/v1/transactions", body), hash.of("PUT", "/v1/transactions", body),
                "the method is part of the signed request");
        assertNotEquals(hash.of("POST", "/v1/transactions", body), hash.of("POST", "/v1/other", body),
                "the path is part of the signed request");
    }

    @Test
    void canonicalFormSortsObjectKeysAtEveryLevel() throws IOException {
        JsonNode body = json("{\"b\":\"2\",\"a\":{\"d\":\"4\",\"c\":\"3\"}}");

        String canonical = JsonMapper.createDefault().writeValueAsString(RequestHash.sorted(body));

        assertEquals("{\"a\":{\"c\":\"3\",\"d\":\"4\"},\"b\":\"2\"}", canonical,
                "the canonical JSON must sort the keys of nested objects too");
    }

    private static JsonNode json(String text) throws IOException {
        return JsonMapper.createDefault().readValue(text, JsonNode.class);
    }
}
