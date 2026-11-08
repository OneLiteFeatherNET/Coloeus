package net.onelitefeather.coloeus.ledger;

import javax.money.MonetaryAmount;

/**
 * One posting of a request: an account and a signed amount. Negative amounts are debits.
 */
public record Leg(AccountRef account, MonetaryAmount amount) {
}
