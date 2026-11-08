package net.onelitefeather.coloeus.account;

import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.onelitefeather.coloeus.money.AmountJson;

/**
 * One page of an account's postings, newest first. {@code nextCursor} is null on the last page.
 */
@Serdeable
public record PostingsResponse(List<PostingView> items, String nextCursor) {

    /**
     * A posting of the account. {@code amount} is signed from the account's view; {@code counterAccounts} are the
     * other accounts of the same transaction.
     */
    @Serdeable
    public record PostingView(
            UUID id,
            UUID transactionId,
            String kind,
            String reason,
            String actor,
            AmountJson amount,
            List<UUID> counterAccounts,
            Instant createdAt) {
    }
}
