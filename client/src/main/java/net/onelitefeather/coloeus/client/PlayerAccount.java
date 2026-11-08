package net.onelitefeather.coloeus.client;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.money.MonetaryAmount;

/**
 * The account of a player.
 *
 * @param id       the account id
 * @param owner    the player UUID
 * @param balances the balance in every currency the account holds
 */
public record PlayerAccount(UUID id, UUID owner, List<MonetaryAmount> balances) {

    /**
     * @param currencyCode the currency code
     * @return the balance in that currency, empty when the account holds no row of it
     */
    public Optional<MonetaryAmount> balanceIn(String currencyCode) {
        return balances.stream()
                .filter(amount -> amount.getCurrency().getCurrencyCode().equals(currencyCode))
                .findFirst();
    }
}
