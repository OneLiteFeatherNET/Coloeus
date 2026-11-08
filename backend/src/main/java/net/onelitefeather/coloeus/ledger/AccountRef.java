package net.onelitefeather.coloeus.ledger;

import java.util.UUID;
import net.onelitefeather.coloeus.account.AccountKind;

/**
 * How a request names an account before it is resolved inside the booking transaction.
 */
public sealed interface AccountRef {

    /** An existing account, addressed by its id. */
    record ById(UUID id) implements AccountRef {
    }

    /** The player account of a UUID; created when it does not exist yet. */
    record Player(UUID owner) implements AccountRef {
    }

    /** The SOURCE or SINK account of the calling client. Only MINT and BURN name it. */
    record ClientSystem(AccountKind kind) implements AccountRef {
    }

    /** The FEE or ADMIN_* account of a currency; created when it does not exist yet. */
    record CurrencySystem(AccountKind kind, String currency) implements AccountRef {
    }
}
