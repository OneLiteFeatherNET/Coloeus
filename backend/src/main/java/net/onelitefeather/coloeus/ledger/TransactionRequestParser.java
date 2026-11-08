package net.onelitefeather.coloeus.ledger;

import io.micronaut.json.tree.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.money.CurrencyUnit;
import net.onelitefeather.coloeus.account.AccountKind;
import net.onelitefeather.coloeus.money.AmountJson;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;
import net.onelitefeather.coloeus.request.RequestValues;
import org.javamoney.moneta.Money;

/**
 * Turns a transaction body into a {@link TransactionCommand}. Pure: currencies are looked up through the given
 * function, so the parser runs without a database.
 */
public final class TransactionRequestParser {

    static final int MAX_METADATA_ENTRIES = 16;
    static final int MAX_REASON_LENGTH = 255;
    static final int MAX_ACTOR_LENGTH = 128;
    static final int MAX_EXTERNAL_REFERENCE_LENGTH = 128;

    private static final Set<String> COMMON_FIELDS =
            Set.of("kind", "reason", "actor", "externalReference", "metadata");
    private static final Map<TransactionKind, Set<String>> KIND_FIELDS = Map.of(
            TransactionKind.TRANSFER, Set.of("from", "to", "amount"),
            TransactionKind.MINT, Set.of("to", "amount"),
            TransactionKind.BURN, Set.of("from", "amount"),
            TransactionKind.BATCH, Set.of("postings"));

    private TransactionRequestParser() {
    }

    public static TransactionCommand parse(JsonNode body, Function<String, Optional<CurrencyUnit>> currencies) {
        if (body == null || !body.isObject()) {
            throw RequestValues.invalid("the request body must be a JSON object");
        }
        TransactionKind kind = kind(body);
        Set<String> allowed = new HashSet<>(COMMON_FIELDS);
        allowed.addAll(KIND_FIELDS.get(kind));
        for (Map.Entry<String, JsonNode> field : body.entries()) {
            if (!allowed.contains(field.getKey())) {
                throw RequestValues.invalid("field " + field.getKey() + " is not allowed for kind " + kind);
            }
        }
        List<Leg> legs = switch (kind) {
            case TRANSFER -> transferLegs(body, currencies);
            case MINT -> mintLegs(body, currencies);
            case BURN -> burnLegs(body, currencies);
            case BATCH -> batchLegs(body, currencies);
            case RESET, ADJUST -> throw RequestValues.invalid("kind must be TRANSFER, MINT, BURN or BATCH");
        };
        return new TransactionCommand(
                kind,
                legs,
                text(body, "reason", MAX_REASON_LENGTH),
                text(body, "actor", MAX_ACTOR_LENGTH),
                text(body, "externalReference", MAX_EXTERNAL_REFERENCE_LENGTH),
                metadata(present(body.get("metadata"))));
    }

    private static List<Leg> transferLegs(JsonNode body, Function<String, Optional<CurrencyUnit>> currencies) {
        AccountRef from = account(required(body, "from"), currencies);
        AccountRef to = account(required(body, "to"), currencies);
        if (from.equals(to)) {
            throw RequestValues.invalid("from and to must be different accounts");
        }
        Money amount = amount(body, currencies);
        return List.of(new Leg(from, amount.negate()), new Leg(to, amount));
    }

    /** MINT books SOURCE(client) to the player. */
    private static List<Leg> mintLegs(JsonNode body, Function<String, Optional<CurrencyUnit>> currencies) {
        AccountRef to = account(required(body, "to"), currencies);
        Money amount = amount(body, currencies);
        return List.of(new Leg(new AccountRef.ClientSystem(AccountKind.SOURCE), amount.negate()), new Leg(to, amount));
    }

    /** BURN books the player to SINK(client). */
    private static List<Leg> burnLegs(JsonNode body, Function<String, Optional<CurrencyUnit>> currencies) {
        AccountRef from = account(required(body, "from"), currencies);
        Money amount = amount(body, currencies);
        return List.of(new Leg(from, amount.negate()), new Leg(new AccountRef.ClientSystem(AccountKind.SINK), amount));
    }

    private static List<Leg> batchLegs(JsonNode body, Function<String, Optional<CurrencyUnit>> currencies) {
        JsonNode postings = required(body, "postings");
        if (!postings.isArray() || postings.size() == 0) {
            throw RequestValues.invalid("postings must be a non-empty array");
        }
        List<Leg> legs = new ArrayList<>();
        for (JsonNode posting : postings.values()) {
            if (!posting.isObject()) {
                throw RequestValues.invalid("each posting must be an object");
            }
            for (Map.Entry<String, JsonNode> field : posting.entries()) {
                if (!Set.of("account", "direction", "amount").contains(field.getKey())) {
                    throw RequestValues.invalid("posting field " + field.getKey() + " is not allowed");
                }
            }
            AccountRef account = account(required(posting, "account"), currencies);
            Money amount = amount(posting, currencies);
            String direction = requiredText(posting, "direction");
            legs.add(switch (direction) {
                case "DEBIT" -> new Leg(account, amount.negate());
                case "CREDIT" -> new Leg(account, amount);
                default -> throw RequestValues.invalid("direction must be DEBIT or CREDIT");
            });
        }
        return legs;
    }

    private static TransactionKind kind(JsonNode body) {
        String value = requiredText(body, "kind");
        TransactionKind kind;
        try {
            kind = TransactionKind.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw RequestValues.invalid("kind must be TRANSFER, MINT, BURN or BATCH");
        }
        if (kind == TransactionKind.RESET || kind == TransactionKind.ADJUST) {
            throw RequestValues.invalid("kind must be TRANSFER, MINT, BURN or BATCH");
        }
        return kind;
    }

    /** An account reference: an account id, {@code {"player": uuid}} or {@code {"system", "currency"}}. */
    static AccountRef account(JsonNode node, Function<String, Optional<CurrencyUnit>> currencies) {
        if (node.isString()) {
            return new AccountRef.ById(RequestValues.uuid(node.getStringValue(), "account"));
        }
        if (node.isObject() && node.size() == 1 && present(node.get("player")) != null) {
            JsonNode player = node.get("player");
            if (!player.isString()) {
                throw RequestValues.invalid("player must be a UUID");
            }
            return new AccountRef.Player(RequestValues.uuid(player.getStringValue(), "player"));
        }
        if (node.isObject() && node.size() == 2 && present(node.get("system")) != null
                && present(node.get("currency")) != null) {
            AccountKind kind = systemKind(requiredText(node, "system"));
            String currency = requiredText(node, "currency");
            if (currencies.apply(currency).isEmpty()) {
                throw ProblemException.of(ProblemCode.CURRENCY_UNKNOWN, "currency " + currency + " is not defined");
            }
            return new AccountRef.CurrencySystem(kind, currency);
        }
        throw RequestValues.invalid(
                "an account must be an account id, {\"player\": uuid} or {\"system\": \"FEE\", \"currency\": code}");
    }

    private static AccountKind systemKind(String value) {
        return switch (value) {
            case "FEE" -> AccountKind.FEE;
            case "ADMIN_SOURCE" -> AccountKind.ADMIN_SOURCE;
            case "ADMIN_SINK" -> AccountKind.ADMIN_SINK;
            default -> throw RequestValues.invalid("system must be FEE, ADMIN_SOURCE or ADMIN_SINK");
        };
    }

    /** The amount of a body: {@code {"amount": "12.50", "currency": "COINS"}}, validated and never rounded. */
    static Money amount(JsonNode owner, Function<String, Optional<CurrencyUnit>> currencies) {
        JsonNode node = required(owner, "amount");
        if (!node.isObject()) {
            throw RequestValues.invalid("amount must be an object with amount and currency");
        }
        for (Map.Entry<String, JsonNode> field : node.entries()) {
            if (!Set.of("amount", "currency").contains(field.getKey())) {
                throw RequestValues.invalid("amount field " + field.getKey() + " is not allowed");
            }
        }
        JsonNode value = present(node.get("amount"));
        if (value == null || !value.isString()) {
            throw ProblemException.of(ProblemCode.AMOUNT_INVALID,
                    "amount must be a plain decimal string such as \"12.50\"");
        }
        String currency = requiredText(node, "currency");
        return new AmountJson(value.getStringValue(), currency).toMoney(currencies);
    }

    static Map<String, String> metadata(JsonNode node) {
        if (node == null) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw RequestValues.invalid("metadata must be an object of strings");
        }
        if (node.size() > MAX_METADATA_ENTRIES) {
            throw ProblemException.of(ProblemCode.METADATA_TOO_LARGE,
                    "metadata may have at most " + MAX_METADATA_ENTRIES + " entries");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : node.entries()) {
            if (!entry.getValue().isString()) {
                throw RequestValues.invalid("metadata values must be strings");
            }
            result.put(entry.getKey(), entry.getValue().getStringValue());
        }
        return result;
    }

    private static String text(JsonNode body, String field, int maxLength) {
        JsonNode node = present(body.get(field));
        if (node == null) {
            return null;
        }
        if (!node.isString()) {
            throw RequestValues.invalid(field + " must be a string");
        }
        String value = node.getStringValue();
        if (value.isBlank()) {
            throw RequestValues.invalid(field + " must not be blank");
        }
        if (value.codePointCount(0, value.length()) > maxLength) {
            throw RequestValues.invalid(field + " must not be longer than " + maxLength + " characters");
        }
        return value;
    }

    private static String requiredText(JsonNode body, String field) {
        JsonNode node = required(body, field);
        if (!node.isString()) {
            throw RequestValues.invalid(field + " must be a string");
        }
        return node.getStringValue();
    }

    private static JsonNode required(JsonNode body, String field) {
        JsonNode node = present(body.get(field));
        if (node == null) {
            throw RequestValues.invalid(field + " is required");
        }
        return node;
    }

    /** A JSON null counts as absent. */
    private static JsonNode present(JsonNode node) {
        return node == null || node.isNull() ? null : node;
    }
}
