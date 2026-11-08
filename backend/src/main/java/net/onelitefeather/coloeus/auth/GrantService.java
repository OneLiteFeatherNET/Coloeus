package net.onelitefeather.coloeus.auth;

import jakarta.inject.Singleton;
import java.sql.Connection;
import java.sql.SQLException;
import net.onelitefeather.coloeus.persistence.JdbcTransactions;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks the registry and the grants of the calling client before it acts.
 *
 * <p>A client without registry entry may read but not write: a read passes, every write is refused with
 * {@link ProblemCode#CLIENT_NOT_REGISTERED}. A registered client needs the grant itself, otherwise
 * {@link ProblemCode#FORBIDDEN_GRANT}. The admin role does not replace a grant; it only bypasses the actor rule of
 * {@link ActorCheck} and the transfer grant of a player account (see the ledger booking).
 *
 * <p>The methods taking a {@link Connection} run inside the caller's database transaction, so a check and the
 * booking it guards see the same registry.
 */
@Singleton
public class GrantService {

    private static final Logger LOG = LoggerFactory.getLogger(GrantService.class);

    private final JdbcTransactions transactions;
    private final ClientRepository repository;

    public GrantService(JdbcTransactions transactions, ClientRepository repository) {
        this.transactions = transactions;
        this.repository = repository;
    }

    /**
     * Checks one permission on one currency in its own read transaction.
     *
     * @throws ProblemException {@link ProblemCode#CLIENT_NOT_REGISTERED} or {@link ProblemCode#FORBIDDEN_GRANT}
     */
    public void require(Caller caller, String currency, Permission permission) {
        transactions.read(connection -> {
            require(connection, caller, currency, permission);
            return null;
        });
    }

    /**
     * Checks one permission on one currency.
     *
     * @throws ProblemException {@link ProblemCode#CLIENT_NOT_REGISTERED} or {@link ProblemCode#FORBIDDEN_GRANT}
     */
    public void require(Connection connection, Caller caller, String currency, Permission permission)
            throws SQLException {
        if (!repository.exists(connection, caller.clientId())) {
            if (permission == Permission.READ) {
                return;
            }
            LOG.warn("refused {} on {}: client {} is not registered", permission, currency, caller.clientId());
            throw ProblemException.of(ProblemCode.CLIENT_NOT_REGISTERED,
                    "client " + caller.clientId() + " is not registered and may not " + permission);
        }
        if (!repository.permissions(connection, caller.clientId(), currency).contains(permission)) {
            LOG.warn("refused {} on {}: client {} holds no such grant", permission, currency, caller.clientId());
            throw ProblemException.of(ProblemCode.FORBIDDEN_GRANT,
                    "client " + caller.clientId() + " has no " + permission + " grant for " + currency);
        }
    }

    /**
     * Refuses a write of an unregistered client. Runs before any row is written, so a refused client leaves nothing.
     *
     * @throws ProblemException {@link ProblemCode#CLIENT_NOT_REGISTERED}
     */
    public void requireRegistered(Connection connection, Caller caller) throws SQLException {
        if (!repository.exists(connection, caller.clientId())) {
            LOG.warn("refused a write: client {} is not registered", caller.clientId());
            throw ProblemException.of(ProblemCode.CLIENT_NOT_REGISTERED,
                    "client " + caller.clientId() + " is not registered and may not write");
        }
    }

    /**
     * Checks that the caller may read accounts. Admins and moderators always may. An unregistered client may read
     * (service-auth spec). A registered client needs the {@link Permission#READ} grant for at least one currency,
     * because account reads are not filtered per currency yet.
     *
     * @throws ProblemException {@link ProblemCode#FORBIDDEN_GRANT}
     */
    public void requireRead(Caller caller) {
        if (caller.admin() || caller.roles().contains(Roles.MODERATOR)) {
            return;
        }
        transactions.read(connection -> {
            if (!repository.exists(connection, caller.clientId())) {
                return null;
            }
            if (!repository.hasAnyPermission(connection, caller.clientId(), Permission.READ)) {
                LOG.warn("refused a read: client {} holds no READ grant", caller.clientId());
                throw ProblemException.of(ProblemCode.FORBIDDEN_GRANT,
                        "client " + caller.clientId() + " has no READ grant for any currency");
            }
            return null;
        });
    }
}
