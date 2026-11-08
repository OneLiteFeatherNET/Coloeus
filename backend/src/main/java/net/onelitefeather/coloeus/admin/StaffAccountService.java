package net.onelitefeather.coloeus.admin;

import jakarta.inject.Singleton;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import net.onelitefeather.coloeus.account.AccountKind;
import net.onelitefeather.coloeus.account.AccountRepository;
import net.onelitefeather.coloeus.account.PostingCursor;
import net.onelitefeather.coloeus.account.AccountService;
import net.onelitefeather.coloeus.money.AmountJson;
import net.onelitefeather.coloeus.persistence.JdbcTransactions;
import net.onelitefeather.coloeus.request.RequestValues;

/**
 * Player accounts for the staff UI: search by name or UUID, the detail with balances, and the postings. Reads only;
 * nothing is created (a UUID without an account is reported with a null {@code accountId}).
 */
@Singleton
public class StaffAccountService {

    static final int SEARCH_LIMIT = 20;
    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 200;

    /** Minecraft user names: 1 to 16 ASCII letters, digits or underscores. */
    static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final JdbcTransactions transactions;
    private final AccountRepository accounts;
    private final StaffQueries queries;

    public StaffAccountService(JdbcTransactions transactions, AccountRepository accounts, StaffQueries queries) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.queries = queries;
    }

    /**
     * Searches the name register by player name, or looks up one UUID. Exactly one of the two must be given. The
     * register answers; the Mojang API is never asked.
     */
    public StaffViews.AccountHits search(String name, String uuid) {
        if ((name == null) == (uuid == null)) {
            throw RequestValues.invalid("give exactly one of name or uuid");
        }
        return transactions.read(connection -> new StaffViews.AccountHits(
                name != null ? byName(connection, name) : byUuid(connection, uuid)));
    }

    private List<StaffViews.AccountHit> byName(Connection connection, String name) throws SQLException {
        if (!NAME.matcher(name).matches()) {
            throw RequestValues.invalid("name must be 1 to 16 letters, digits or underscores");
        }
        List<StaffViews.AccountHit> hits = new ArrayList<>();
        for (StaffQueries.NameRow row : queries.namesHeldBy(connection, name, SEARCH_LIMIT)) {
            hits.add(new StaffViews.AccountHit(row.uuid().toString(), row.name(), row.lastSeen(),
                    playerAccountOf(connection, row.uuid())));
        }
        return hits;
    }

    private List<StaffViews.AccountHit> byUuid(Connection connection, String text) throws SQLException {
        UUID uuid = RequestValues.uuid(text, "uuid");
        Optional<StaffQueries.NameRow> newest = queries.newestNameOf(connection, uuid);
        return List.of(new StaffViews.AccountHit(
                uuid.toString(),
                newest.map(StaffQueries.NameRow::name).orElse(null),
                newest.map(StaffQueries.NameRow::lastSeen).orElse(null),
                playerAccountOf(connection, uuid)));
    }

    private String playerAccountOf(Connection connection, UUID uuid) throws SQLException {
        return queries.playerAccountOf(connection, uuid).map(UUID::toString).orElse(null);
    }

    /** One account with its owner or client, its currency and its balance in every currency. */
    public StaffViews.AccountDetail detail(UUID id) {
        return transactions.read(connection -> {
            AccountRepository.AccountRow account = accounts.find(connection, id).orElseThrow(() -> AccountService.notFound(id));
            StaffViews.OwnerView owner = null;
            StaffViews.ClientRef client = null;
            if (account.kind() == AccountKind.PLAYER) {
                owner = new StaffViews.OwnerView(account.owner().toString(),
                        queries.newestNameOf(connection, account.owner()).map(StaffQueries.NameRow::name).orElse(null));
            }
            if (account.kind() == AccountKind.SOURCE || account.kind() == AccountKind.SINK) {
                client = queries.clientName(connection, account.clientId())
                        .map(name -> new StaffViews.ClientRef(name.clientId().toString(), name.displayName()))
                        .orElse(null);
            }
            List<AmountJson> balances = new ArrayList<>();
            for (AccountRepository.BalanceRow row : accounts.balances(connection, id)) {
                balances.add(AmountJson.of(row.amount()));
            }
            Optional<Instant> latest = accounts.latestPosting(connection, id).map(AccountRepository.PostingPosition::createdAt);
            return new StaffViews.AccountDetail(
                    id.toString(),
                    account.kind().name(),
                    owner,
                    client,
                    account.currency(),
                    balances,
                    latest.orElse(null));
        });
    }

    /** One page of an account's postings, newest first, with the cursor of the next page. */
    public StaffViews.StaffPostingsPage postings(UUID id, Integer limit, String cursor) {
        int size = limit == null ? DEFAULT_PAGE_SIZE : limit;
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw RequestValues.invalid("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        PostingCursor after = cursor == null ? null : PostingCursor.decode(cursor);
        return transactions.read(connection -> {
            accounts.find(connection, id).orElseThrow(() -> AccountService.notFound(id));
            List<StaffViews.StaffPosting> rows = queries.postings(connection, id, after, size + 1);
            boolean more = rows.size() > size;
            List<StaffViews.StaffPosting> page = more ? rows.subList(0, size) : rows;
            String next = null;
            if (more) {
                StaffViews.StaffPosting last = page.get(page.size() - 1);
                next = new PostingCursor(last.createdAt(), UUID.fromString(last.id())).encode();
            }
            return new StaffViews.StaffPostingsPage(page, next);
        });
    }
}
