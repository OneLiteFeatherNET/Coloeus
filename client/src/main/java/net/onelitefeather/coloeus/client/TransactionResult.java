package net.onelitefeather.coloeus.client;

import java.util.List;
import java.util.UUID;

/**
 * A booked transaction: its id and the balances of the player accounts it touched after booking.
 *
 * @param id       the transaction id
 * @param balances the resulting balances of the player accounts
 */
public record TransactionResult(UUID id, List<AccountBalance> balances) {
}
