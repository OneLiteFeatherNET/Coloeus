package net.onelitefeather.coloeus.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import javax.money.CurrencyUnit;
import javax.money.MonetaryAmount;
import javax.money.MonetaryContext;
import javax.money.MonetaryContextBuilder;
import org.javamoney.moneta.Money;

/**
 * The only place where JSON is read and written, and the only place where an amount becomes a {@link MonetaryAmount}
 * from its JSON decimal string. Amounts are {@code {"amount": "12.50", "currency": "COINS"}}.
 */
final class ColoeusJson {

    /** The precision of the ledger's amounts: 34 significant digits, as {@link MathContext#DECIMAL128}. */
    static final MonetaryContext DECIMAL128 = MonetaryContextBuilder.of().setPrecision(34).build();

    /** A problem document of the server: its stable code and its detail. */
    record Problem(String code, String detail) {
    }

    private ColoeusJson() {
    }

    static JsonObject object(String text) {
        try {
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new ColoeusException("answer of Coloeus is not a JSON object", e);
        }
    }

    static List<CurrencyDefinition> currencies(String text) {
        JsonArray array;
        try {
            array = JsonParser.parseString(text).getAsJsonArray();
        } catch (JsonParseException | IllegalStateException e) {
            throw new ColoeusException("currency list of Coloeus is not a JSON array", e);
        }
        List<CurrencyDefinition> result = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject o = element.getAsJsonObject();
            result.add(new CurrencyDefinition(
                    string(o, "code"),
                    string(o, "nameSingular"),
                    string(o, "namePlural"),
                    string(o, "symbol"),
                    integer(o, "fractionDigits"),
                    bool(o, "defaultCurrency")));
        }
        return result;
    }

    static UUID uuid(JsonObject object, String field) {
        String text = string(object, field);
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            throw new ColoeusException("answer of Coloeus has no UUID in " + field, e);
        }
    }

    static PlayerAccount playerAccount(String text, Function<String, CurrencyUnit> units) {
        JsonObject o = object(text);
        return new PlayerAccount(uuid(o, "id"), uuid(o, "owner"), amounts(array(o, "balances"), units));
    }

    static List<MonetaryAmount> amounts(JsonArray array, Function<String, CurrencyUnit> units) {
        List<MonetaryAmount> result = new ArrayList<>();
        for (JsonElement element : array) {
            result.add(amount(element.getAsJsonObject(), units));
        }
        return result;
    }

    static TransactionResult transactionResult(String text, Function<String, CurrencyUnit> units) {
        JsonObject o = object(text);
        List<AccountBalance> balances = new ArrayList<>();
        for (JsonElement element : array(o, "balances")) {
            JsonObject line = element.getAsJsonObject();
            balances.add(new AccountBalance(uuid(line, "account"),
                    amount(objectOf(line, "amount"), units)));
        }
        return new TransactionResult(uuid(o, "id"), balances);
    }

    static String transactionBody(Transaction transaction) {
        JsonObject body = new JsonObject();
        body.addProperty("kind", transaction.kind().name());
        switch (transaction.kind()) {
            case MINT -> body.add("to", player(transaction.to()));
            case BURN -> body.add("from", player(transaction.from()));
            case TRANSFER -> {
                body.add("from", player(transaction.from()));
                body.add("to", player(transaction.to()));
                // The debited player acts for itself; the ledger refuses a transfer whose actor is another player.
                body.addProperty("actor", transaction.from().toString());
            }
        }
        body.add("amount", amountJson(transaction.amount()));
        if (transaction.reason() != null) {
            body.addProperty("reason", transaction.reason());
        }
        return body.toString();
    }

    static String nameBody(String name) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        return body.toString();
    }

    /**
     * @return the problem document of an answer, or empty when the body is not a problem document
     */
    static Optional<Problem> problem(String text) {
        try {
            JsonObject o = JsonParser.parseString(text).getAsJsonObject();
            if (!o.has("code") || !o.get("code").isJsonPrimitive()) {
                return Optional.empty();
            }
            return Optional.of(new Problem(string(o, "code"), o.has("detail") && o.get("detail").isJsonPrimitive()
                    ? o.get("detail").getAsString() : ""));
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException e) {
            return Optional.empty();
        }
    }

    private static JsonObject player(UUID uuid) {
        JsonObject player = new JsonObject();
        player.addProperty("player", uuid.toString());
        return player;
    }

    private static JsonObject amountJson(MonetaryAmount amount) {
        JsonObject json = new JsonObject();
        json.addProperty("amount", amount.getNumber().numberValue(BigDecimal.class).toPlainString());
        json.addProperty("currency", amount.getCurrency().getCurrencyCode());
        return json;
    }

    private static MonetaryAmount amount(JsonObject o, Function<String, CurrencyUnit> units) {
        String currency = string(o, "currency");
        BigDecimal value;
        try {
            value = new BigDecimal(string(o, "amount"));
        } catch (NumberFormatException e) {
            throw new ColoeusException("answer of Coloeus has an amount that is not a decimal", e);
        }
        return Money.of(value, units.apply(currency), DECIMAL128);
    }

    private static JsonObject objectOf(JsonObject o, String field) {
        JsonElement element = o.get(field);
        if (element == null || !element.isJsonObject()) {
            throw new ColoeusException("answer of Coloeus has no object " + field);
        }
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonObject o, String field) {
        JsonElement element = o.get(field);
        if (element == null || !element.isJsonArray()) {
            throw new ColoeusException("answer of Coloeus has no array " + field);
        }
        return element.getAsJsonArray();
    }

    private static String string(JsonObject o, String field) {
        JsonElement element = o.get(field);
        if (element == null || !element.isJsonPrimitive()) {
            throw new ColoeusException("answer of Coloeus has no text " + field);
        }
        return element.getAsString();
    }

    private static int integer(JsonObject o, String field) {
        JsonElement element = o.get(field);
        if (element == null || !element.isJsonPrimitive()) {
            throw new ColoeusException("answer of Coloeus has no number " + field);
        }
        return element.getAsInt();
    }

    private static boolean bool(JsonObject o, String field) {
        JsonElement element = o.get(field);
        if (element == null || !element.isJsonPrimitive()) {
            throw new ColoeusException("answer of Coloeus has no flag " + field);
        }
        return element.getAsBoolean();
    }
}
