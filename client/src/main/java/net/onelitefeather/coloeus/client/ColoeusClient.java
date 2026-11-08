package net.onelitefeather.coloeus.client;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import javax.money.MonetaryAmount;

/**
 * The calls of the Coloeus API used by the Minecraft side. Every amount is a {@link MonetaryAmount}. Every call takes
 * the time it may wait; reads fail with {@link ColoeusUnavailableException} after that time. Writes take a budget
 * that covers token acquisition, the first attempt and at most one retry with the same idempotency key.
 */
public interface ColoeusClient {

    /** The timeout of a call when a caller gives none: the bound a call on a server's main thread relies on. */
    Duration DEFAULT_TIMEOUT = Duration.ofMillis(500);

    /**
     * @param timeout the time the call may take
     * @return every currency of the network, with the default one marked
     */
    List<CurrencyDefinition> currencies(Duration timeout);

    /**
     * @param name    the player name
     * @param timeout the time the call may take
     * @return the UUID that holds the name
     * @throws PlayerUnknownException        when neither the register nor the Mojang profile API knows the name
     * @throws NameLookupUnavailableException when the Mojang profile API failed or did not answer in time
     */
    UUID playerByName(String name, Duration timeout);

    /**
     * Reports the name a player joined with.
     *
     * @param uuid           the player
     * @param name           the name the player joined with
     * @param idempotencyKey the key of this report
     * @param budget         the total time of the call
     */
    void reportName(UUID uuid, String name, String idempotencyKey, Duration budget);

    /**
     * @param uuid    the player
     * @param timeout the time the call may take
     * @return the player account, created on first access, with its balances
     */
    PlayerAccount playerAccount(UUID uuid, Duration timeout);

    /**
     * @param accountId the account
     * @param timeout   the time the call may take
     * @return the balance of the account in every currency
     */
    List<MonetaryAmount> balances(UUID accountId, Duration timeout);

    /**
     * Books a transaction once per idempotency key.
     *
     * @param transaction    the transaction
     * @param idempotencyKey the key of this booking; a retry uses the same key
     * @param budget         the total time of the call
     * @return the postings and the player balances after the booking
     * @throws InsufficientFundsException when the debited player would fall below zero
     * @throws ForbiddenGrantException    when the client holds no grant for the kind of transaction
     */
    TransactionResult transact(Transaction transaction, String idempotencyKey, Duration budget);
}
