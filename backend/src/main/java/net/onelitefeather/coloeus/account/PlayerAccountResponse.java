package net.onelitefeather.coloeus.account;

import io.micronaut.serde.annotation.Serdeable;
import net.onelitefeather.coloeus.money.AmountJson;

import java.util.List;
import java.util.UUID;

/**
 * The player account of a UUID with its balance in every currency.
 */
@Serdeable
public record PlayerAccountResponse(UUID id, String kind, UUID owner, List<AmountJson> balances) {
}
