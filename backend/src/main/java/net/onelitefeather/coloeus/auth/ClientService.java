package net.onelitefeather.coloeus.auth;

import jakarta.inject.Singleton;
import net.onelitefeather.coloeus.persistence.JdbcTransactions;
import net.onelitefeather.coloeus.persistence.UncheckedSqlException;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The client registry: which Entra clients may write, and with which grants per currency. Admin only (see the
 * controller).
 */
@Singleton
public class ClientService {

    static final int MAX_DISPLAY_NAME_LENGTH = 128;

    private final JdbcTransactions transactions;
    private final ClientRepository repository;
    private final Clock clock;

    public ClientService(JdbcTransactions transactions, ClientRepository repository, Clock clock) {
        this.transactions = transactions;
        this.repository = repository;
        this.clock = clock;
    }

    public ClientView register(RegisterClientRequest request) {
        UUID clientId = parseClientId(request.clientId());
        String displayName = requireDisplayName(request.displayName());
        Instant now = clock.instant();
        try {
            transactions.inTransaction(connection -> {
                repository.insert(connection, clientId, displayName, now);
                return null;
            });
        } catch (UncheckedSqlException e) {
            if (e.isState(UncheckedSqlException.UNIQUE_VIOLATION)) {
                throw ProblemException.of(ProblemCode.CLIENT_EXISTS, "client " + clientId + " is already registered");
            }
            throw e;
        }
        return new ClientView(clientId, displayName, now, List.of());
    }

    public List<ClientView> list() {
        return transactions.read(connection -> {
            List<ClientView> views = new ArrayList<>();
            for (ClientRepository.ClientRecord client : repository.findAll(connection)) {
                views.add(new ClientView(client.clientId(), client.displayName(), client.createdAt(),
                        repository.findGrants(connection, client.clientId())));
            }
            return views;
        });
    }

    /**
     * Replaces all grants of a client. Duplicates in the request are stored once.
     *
     * @throws ProblemException {@link ProblemCode#CLIENT_NOT_FOUND} or {@link ProblemCode#CURRENCY_NOT_FOUND}
     */
    public List<GrantView> replaceGrants(String clientIdText, List<GrantRequest> requests) {
        UUID clientId = parseClientId(clientIdText);
        if (requests == null) {
            throw ProblemException.of(ProblemCode.REQUEST_INVALID, "the body must be a list of grants");
        }
        List<GrantView> grants = requests.stream()
                .map(ClientService::toGrant)
                .distinct()
                .toList();
        return transactions.inTransaction(connection -> {
            if (!repository.lockClient(connection, clientId)) {
                throw ProblemException.of(ProblemCode.CLIENT_NOT_FOUND, "client " + clientId + " is not registered");
            }
            for (GrantView grant : grants) {
                if (!repository.currencyExists(connection, grant.currency())) {
                    throw ProblemException.of(ProblemCode.CURRENCY_NOT_FOUND,
                            "currency " + grant.currency() + " does not exist");
                }
            }
            repository.deleteGrants(connection, clientId);
            for (GrantView grant : grants) {
                repository.insertGrant(connection, clientId, grant);
            }
            return repository.findGrants(connection, clientId);
        });
    }

    private static GrantView toGrant(GrantRequest request) {
        if (request == null || request.currency() == null || request.currency().isBlank()) {
            throw ProblemException.of(ProblemCode.REQUEST_INVALID, "every grant needs a currency");
        }
        return new GrantView(request.currency().trim(), Permission.parse(request.permission()));
    }

    static UUID parseClientId(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ProblemException.of(ProblemCode.REQUEST_INVALID, "clientId must be a UUID");
        }
    }

    private static String requireDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw ProblemException.of(ProblemCode.REQUEST_INVALID, "displayName must not be blank");
        }
        if (displayName.codePointCount(0, displayName.length()) > MAX_DISPLAY_NAME_LENGTH) {
            throw ProblemException.of(ProblemCode.REQUEST_INVALID,
                    "displayName must not be longer than " + MAX_DISPLAY_NAME_LENGTH + " characters");
        }
        return displayName;
    }
}
