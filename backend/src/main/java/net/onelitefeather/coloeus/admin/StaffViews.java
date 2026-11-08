package net.onelitefeather.coloeus.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.onelitefeather.coloeus.money.AmountJson;

/**
 * The shapes of the staff API ({@code staff-api} spec, JSON shapes). A field that is not listed there does not exist.
 * Aggregates are plain decimal strings; balances and postings are {@link AmountJson}.
 */
public final class StaffViews {

    private StaffViews() {
    }

    /** A player found by name or UUID. {@code accountId} is null while the player has no account. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AccountHit(String uuid, String name, Instant lastSeen, String accountId) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AccountHits(List<AccountHit> items) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record OwnerView(String uuid, String name) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ClientRef(String clientId, String displayName) {
    }

    /** An account with its owner or client, its currency where it has one, and a balance per currency. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AccountDetail(
            String id,
            String kind,
            OwnerView owner,
            ClientRef client,
            String currency,
            List<AmountJson> balances,
            Instant latestPostingAt) {
    }

    /** A posting of an account; {@code amount} is signed from the account's view. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StaffPosting(
            String id,
            String transactionId,
            String kind,
            String reason,
            String actor,
            String actorName,
            String currency,
            String amount,
            List<String> counterAccounts,
            Instant createdAt) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StaffPostingsPage(List<StaffPosting> items, String nextCursor) {
    }

    /** The movements of a staff write, signed from the player account's view. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StaffBooking(
            String transactionId,
            String kind,
            String accountId,
            Instant createdAt,
            List<AmountJson> movements) {
    }

    /** The money flow of one currency: created and destroyed per window, and the current circulation. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CurrencyFlow(
            String currency,
            String created,
            String destroyed,
            String netInWindow,
            String circulation) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ClientCurrencyFlow(String currency, String created, String destroyed, String netInWindow) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ClientFlow(String clientId, String displayName, List<ClientCurrencyFlow> currencies) {
    }

    /** The staff bucket of one currency: ADMIN_SOURCE debits and ADMIN_SINK credits in the window. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StaffFlow(String currency, String credited, String debited, String netInWindow) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MoneyFlow(
            String window,
            Instant from,
            Instant to,
            List<CurrencyFlow> currencies,
            List<ClientFlow> clients,
            List<StaffFlow> staff) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DayFlow(String date, String created, String destroyed) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MoneySeries(String currency, List<DayFlow> days) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ClientsResponse(List<net.onelitefeather.coloeus.auth.ClientView> items) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CurrenciesResponse(List<net.onelitefeather.coloeus.currency.Currency> items) {
    }

    /** An entry of the staff action log. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StaffAction(
            String id,
            String actorOid,
            String actorName,
            String action,
            String target,
            String transactionId,
            Map<String, String> detail,
            Instant createdAt) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StaffActionsPage(List<StaffAction> items, String nextCursor) {
    }
}
