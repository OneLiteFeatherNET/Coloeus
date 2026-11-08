package net.onelitefeather.coloeus.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Function;
import javax.money.CurrencyUnit;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;
import org.javamoney.moneta.CurrencyUnitBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class TransactionRequestParserTest {

    private static final CurrencyUnit COINS = CurrencyUnitBuilder.of("COINS", "test")
            .setDefaultFractionDigits(2)
            .build();
    private static final Function<String, Optional<CurrencyUnit>> CURRENCIES =
            code -> "COINS".equals(code) ? Optional.of(COINS) : Optional.empty();

    private static final String ALICE = "00000000-0000-0000-0000-00000000000a";
    private static final String BOB = "00000000-0000-0000-0000-00000000000b";

    @Test
    void transferDebitsThePayerAndCreditsThePayee() throws IOException {
        TransactionCommand command = parse(transfer("{\"player\":\"" + ALICE + "\"}",
                "{\"player\":\"" + BOB + "\"}", "4.00"));

        assertEquals(TransactionKind.TRANSFER, command.kind(), "the kind must be TRANSFER");
        assertTrue(command.legs().get(0).amount().isNegative(), "the payer's leg must be a debit");
        assertEquals(0, new BigDecimal("4.00").compareTo(
                        command.legs().get(1).amount().getNumber().numberValueExact(BigDecimal.class)),
                "the payee's leg must credit 4.00");
    }

    @Test
    void transferToTheSameAccountIsRejected() throws IOException {
        String body = transfer("{\"player\":\"" + ALICE + "\"}", "{\"player\":\"" + ALICE + "\"}", "1.00");

        assertCode(ProblemCode.REQUEST_INVALID, () -> parse(body),
                "a transfer from an account to itself must be rejected");
    }

    @Test
    void fieldOfAnotherKindIsRejected() throws IOException {
        String body = "{\"kind\":\"MINT\",\"from\":{\"player\":\"" + ALICE + "\"},"
                + "\"to\":{\"player\":\"" + BOB + "\"},\"amount\":" + amount("1.00") + "}";

        assertCode(ProblemCode.REQUEST_INVALID, () -> parse(body), "MINT must not accept the field from");
    }

    @Test
    void unknownFieldIsRejected() throws IOException {
        String body = transferWith(",\"amountt\":1");

        assertCode(ProblemCode.REQUEST_INVALID, () -> parse(body), "an unknown field must be rejected, not ignored");
    }

    @Test
    void seventeenMetadataEntriesAreTooLarge() throws IOException {
        StringBuilder metadata = new StringBuilder("{");
        for (int i = 0; i < 17; i++) {
            metadata.append(i == 0 ? "" : ",").append("\"k").append(i).append("\":\"v\"");
        }
        metadata.append("}");
        String body = transferWith(",\"metadata\":" + metadata);

        assertCode(ProblemCode.METADATA_TOO_LARGE, () -> parse(body), "17 metadata entries must be too large");
    }

    @Test
    void nonStringMetadataValueIsRejected() throws IOException {
        String body = transferWith(",\"metadata\":{\"round\":1}");

        assertCode(ProblemCode.REQUEST_INVALID, () -> parse(body), "metadata values must be strings");
    }

    @Test
    void unbalancedBatchIsRejected() throws IOException {
        String body = batch(
                "{\"account\":\"" + ALICE + "\",\"direction\":\"DEBIT\",\"amount\":" + amount("1.00") + "}",
                "{\"account\":\"" + BOB + "\",\"direction\":\"CREDIT\",\"amount\":" + amount("2.00") + "}");

        assertCode(ProblemCode.UNBALANCED, () -> parse(body),
                "a batch that debits 1.00 and credits 2.00 must be unbalanced");
    }

    @Test
    void balancedBatchIsAccepted() throws IOException {
        String body = batch(
                "{\"account\":\"" + ALICE + "\",\"direction\":\"DEBIT\",\"amount\":" + amount("2.00") + "}",
                "{\"account\":\"" + BOB + "\",\"direction\":\"CREDIT\",\"amount\":" + amount("2.00") + "}");

        TransactionCommand command = parse(body);

        assertEquals(2, command.legs().size(), "both postings of a balanced batch must be kept");
    }

    @Test
    void batchDirectionMustBeDebitOrCredit() throws IOException {
        String body = batch(
                "{\"account\":\"" + ALICE + "\",\"direction\":\"LEFT\",\"amount\":" + amount("2.00") + "}",
                "{\"account\":\"" + BOB + "\",\"direction\":\"CREDIT\",\"amount\":" + amount("2.00") + "}");

        assertCode(ProblemCode.REQUEST_INVALID, () -> parse(body), "the direction must be DEBIT or CREDIT");
    }

    @Test
    void amountWithMoreFractionDigitsIsScaleExceeded() throws IOException {
        String body = transfer("{\"player\":\"" + ALICE + "\"}", "{\"player\":\"" + BOB + "\"}", "1.005");

        assertCode(ProblemCode.SCALE_EXCEEDED, () -> parse(body), "1.005 COINS must not be rounded but rejected");
    }

    @Test
    void zeroAmountIsAmountInvalid() throws IOException {
        String body = transfer("{\"player\":\"" + ALICE + "\"}", "{\"player\":\"" + BOB + "\"}", "0.00");

        assertCode(ProblemCode.AMOUNT_INVALID, () -> parse(body), "a zero amount must be rejected");
    }

    @Test
    void amountAsJsonNumberIsAmountInvalid() throws IOException {
        String body = "{\"kind\":\"MINT\",\"to\":{\"player\":\"" + BOB + "\"},"
                + "\"amount\":{\"amount\":1.5,\"currency\":\"COINS\"}}";

        assertCode(ProblemCode.AMOUNT_INVALID, () -> parse(body), "an amount must be a string, never a JSON number");
    }

    @Test
    void unknownCurrencyIsCurrencyUnknown() throws IOException {
        String body = "{\"kind\":\"MINT\",\"to\":{\"player\":\"" + BOB + "\"},"
                + "\"amount\":{\"amount\":\"1.00\",\"currency\":\"GEMS\"}}";

        assertCode(ProblemCode.CURRENCY_UNKNOWN, () -> parse(body), "GEMS is not served and must be unknown");
    }

    @Test
    void unknownKindIsRejected() throws IOException {
        String body = "{\"kind\":\"WITHDRAW\"}";

        assertCode(ProblemCode.REQUEST_INVALID, () -> parse(body), "an unknown kind must be rejected");
    }

    private static TransactionCommand parse(String json) throws IOException {
        JsonNode body = JsonMapper.createDefault().readValue(json, JsonNode.class);
        return TransactionRequestParser.parse(body, CURRENCIES);
    }

    private static String transfer(String from, String to, String amount) {
        return "{\"kind\":\"TRANSFER\",\"from\":" + from + ",\"to\":" + to + ",\"amount\":" + amount(amount) + "}";
    }

    /** A valid transfer of 1.00 from Alice to Bob with extra top-level fields appended. */
    private static String transferWith(String extraFields) {
        return "{\"kind\":\"TRANSFER\",\"from\":{\"player\":\"" + ALICE + "\"},\"to\":{\"player\":\"" + BOB
                + "\"},\"amount\":" + amount("1.00") + extraFields + "}";
    }

    private static String batch(String... postings) {
        return "{\"kind\":\"BATCH\",\"postings\":[" + String.join(",", postings) + "]}";
    }

    private static String amount(String value) {
        return "{\"amount\":\"" + value + "\",\"currency\":\"COINS\"}";
    }

    private static void assertCode(ProblemCode code, Executable action, String message) {
        ProblemException failure = assertThrows(ProblemException.class, action, message);
        assertEquals(code, failure.code(), message + " (was " + failure.code() + ")");
    }
}
