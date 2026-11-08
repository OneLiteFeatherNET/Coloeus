package net.onelitefeather.coloeus.ledger;

import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The request hash of an idempotent write: SHA-256 over method, path and the canonical JSON of the body. The
 * canonical form sorts object keys, so the same request hashes the same whatever the client's key order.
 */
@Singleton
public class RequestHash {

    private final JsonMapper json;

    public RequestHash(JsonMapper json) {
        this.json = json;
    }

    public String of(String method, String path, JsonNode body) {
        try {
            String canonical = json.writeValueAsString(sorted(body));
            String signed = method + " " + path + "\n" + canonical;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signed.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("request hash failed", e);
        }
    }

    /** The body with every object's keys in sorted order; arrays keep their order. */
    static JsonNode sorted(JsonNode node) {
        if (node.isObject()) {
            Map<String, JsonNode> fields = new TreeMap<>();
            for (Map.Entry<String, JsonNode> entry : node.entries()) {
                fields.put(entry.getKey(), sorted(entry.getValue()));
            }
            return JsonNode.createObjectNode(new LinkedHashMap<>(fields));
        }
        if (node.isArray()) {
            List<JsonNode> items = new ArrayList<>();
            for (JsonNode item : node.values()) {
                items.add(sorted(item));
            }
            return JsonNode.createArrayNode(items);
        }
        return node;
    }
}
