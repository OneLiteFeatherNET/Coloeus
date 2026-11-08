package net.onelitefeather.coloeus.ledger;

import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.onelitefeather.coloeus.money.AmountJson;

/**
 * A booked transaction: its postings and the balances of the player accounts it touched after booking.
 */
@Serdeable
public record TransactionResponse(
        UUID id,
        String kind,
        Instant createdAt,
        String reason,
        String actor,
        String externalReference,
        Map<String, String> metadata,
        List<PostingLine> postings,
        List<BalanceLine> balances) {

    /** One posting; the amount is signed. */
    @Serdeable
    public record PostingLine(UUID account, AmountJson amount) {
    }

    /** The balance of a player account after the transaction, in the currency it was booked in. */
    @Serdeable
    public record BalanceLine(UUID account, AmountJson amount) {
    }
}
