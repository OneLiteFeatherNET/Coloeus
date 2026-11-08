package net.onelitefeather.coloeus.account;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;
import net.onelitefeather.coloeus.request.RequestValues;

/**
 * Position in the posting history of an account: the created time and id of the last posting that was returned.
 * Clients see it as an opaque base64url string.
 */
public record PostingCursor(Instant createdAt, UUID postingId) {

    public String encode() {
        String text = createdAt + "|" + postingId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    public static PostingCursor decode(String value) {
        try {
            String text = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            int separator = text.indexOf('|');
            if (separator < 0) {
                throw RequestValues.invalid("cursor is not valid");
            }
            return new PostingCursor(
                    Instant.parse(text.substring(0, separator)),
                    UUID.fromString(text.substring(separator + 1)));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw RequestValues.invalid("cursor is not valid");
        }
    }
}
