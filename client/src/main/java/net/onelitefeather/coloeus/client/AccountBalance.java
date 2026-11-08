package net.onelitefeather.coloeus.client;

import java.util.UUID;
import javax.money.MonetaryAmount;

/**
 * The balance of one account after a transaction.
 *
 * @param accountId the account id
 * @param amount    the balance in the currency it was booked in
 */
public record AccountBalance(UUID accountId, MonetaryAmount amount) {
}
