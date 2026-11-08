package net.onelitefeather.coloeus.admin;

import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.onelitefeather.coloeus.auth.ClientService;
import net.onelitefeather.coloeus.auth.ClientView;
import net.onelitefeather.coloeus.auth.GrantRequest;
import net.onelitefeather.coloeus.auth.GrantView;
import net.onelitefeather.coloeus.auth.Permission;
import net.onelitefeather.coloeus.auth.RegisterClientRequest;
import net.onelitefeather.coloeus.currency.CreateCurrencyRequest;
import net.onelitefeather.coloeus.currency.Currency;
import net.onelitefeather.coloeus.currency.CurrencyService;
import net.onelitefeather.coloeus.ledger.IdempotentWriter;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;
import net.onelitefeather.coloeus.request.RequestValues;

/**
 * The configuration writes of the staff API: the client registry, the grants, and the currencies. None of them books
 * money, so each writes its {@code staff_action} row and no journal transaction (staff-operations spec). Each runs in
 * one database transaction with its idempotency key.
 */
@Singleton
public class StaffConfigService {

    private final ClientService clients;
    private final CurrencyService currencies;
    private final StaffAudit audit;
    private final StaffWrites writes;

    public StaffConfigService(ClientService clients, CurrencyService currencies, StaffAudit audit, StaffWrites writes) {
        this.clients = clients;
        this.currencies = currencies;
        this.audit = audit;
        this.writes = writes;
    }

    /** Registers a client without grants. Answers the new client as JSON. */
    public String registerClient(StaffActor actor, RegisterClientRequest body, String key, String requestHash) {
        return writes.run(actor, key, requestHash, (connection, now) -> {
            ClientView view = clients.registerIn(connection, body.clientId(), body.displayName(), now);
            audit.record(connection, actor, StaffAudit.StaffAction.CLIENT_REGISTERED, view.clientId().toString(), null,
                    Map.of("displayName", view.displayName()), now);
            return new IdempotentWriter.Outcome(null, writes.json(view));
        });
    }

    /** Replaces all grants of a client. Duplicates and unknown currencies are refused before the write. */
    public String replaceGrants(StaffActor actor, String clientId, StaffBodies.Grants body, String key,
                                String requestHash) {
        List<GrantRequest> requests = body.grants();
        if (requests == null) {
            throw RequestValues.invalid("grants must be a list");
        }
        List<GrantView> grants = requests.stream().map(StaffConfigService::grantOf).toList();
        if (grants.stream().distinct().count() != grants.size()) {
            throw RequestValues.invalid("a grant of a currency and permission is listed twice");
        }
        for (GrantView grant : grants) {
            if (currencies.lookup(grant.currency()).isEmpty()) {
                throw ProblemException.of(ProblemCode.CURRENCY_UNKNOWN,
                        "currency " + grant.currency() + " is not defined");
            }
        }
        String summary = grants.stream()
                .map(grant -> grant.currency() + ":" + grant.permission().name())
                .collect(Collectors.joining(","));
        return writes.run(actor, key, requestHash, (connection, now) -> {
            ClientView view = clients.replaceGrantsIn(connection, clientId, requests);
            audit.record(connection, actor, StaffAudit.StaffAction.GRANTS_REPLACED, view.clientId().toString(), null,
                    Map.of("grants", summary), now);
            return new IdempotentWriter.Outcome(null, writes.json(view));
        });
    }

    /** Creates a currency with the ledger's rules; the provider of this replica is reloaded after the commit. */
    public String createCurrency(StaffActor actor, CreateCurrencyRequest body, String key, String requestHash) {
        String answer = writes.run(actor, key, requestHash, (connection, now) -> {
            Currency created = currencies.createIn(connection, body, now);
            audit.record(connection, actor, StaffAudit.StaffAction.CURRENCY_CREATED, created.code(), null,
                    Map.of("fractionDigits", String.valueOf(created.fractionDigits())), now);
            return new IdempotentWriter.Outcome(null, writes.json(created));
        });
        currencies.reloadProvider();
        return answer;
    }

    /** Makes a currency the single default; the provider of this replica is reloaded after the commit. */
    public String makeDefault(StaffActor actor, String code, String key, String requestHash) {
        String answer = writes.run(actor, key, requestHash, (connection, now) -> {
            Currency result = currencies.makeDefaultIn(connection, code, now);
            audit.record(connection, actor, StaffAudit.StaffAction.CURRENCY_DEFAULT_SET, result.code(), null,
                    Map.of(), now);
            return new IdempotentWriter.Outcome(null, writes.json(result));
        });
        currencies.reloadProvider();
        return answer;
    }

    /** A grant request as a grant; an unknown permission or a missing currency is refused. */
    private static GrantView grantOf(GrantRequest request) {
        if (request == null || request.currency() == null || request.currency().isBlank()) {
            throw RequestValues.invalid("every grant needs a currency");
        }
        return new GrantView(request.currency().trim(), Permission.parse(request.permission()));
    }
}
