package net.onelitefeather.coloeus.account;

import io.micronaut.serde.annotation.Serdeable;
import net.onelitefeather.coloeus.money.AmountJson;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The balances of an account in every currency, with the account's latest posting as journal position.
 * The latest posting fields are null while the account has no postings.
 */
@Serdeable
public record BalancesResponse(
        UUID accountId,
        String kind,
        UUID latestPostingId,
        Instant latestPostingAt,
        List<AmountJson> balances) {
}
