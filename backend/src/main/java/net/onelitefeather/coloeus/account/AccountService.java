package net.onelitefeather.coloeus.account;

import jakarta.inject.Singleton;
import net.onelitefeather.coloeus.money.AmountJson;
import net.onelitefeather.coloeus.persistence.JdbcTransactions;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;
import net.onelitefeather.coloeus.request.RequestValues;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Accounts and balances. Reading a player account creates it when it does not exist yet.
 */
@Singleton
public class AccountService {

    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 200;

    private final JdbcTransactions transactions;
    private final AccountRepository accounts;
    private final Clock clock;

    public AccountService(JdbcTransactions transactions, AccountRepository accounts, Clock clock) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.clock = clock;
    }

    /** The player account of a UUID, created on first access, with its balance in every currency. */
    public PlayerAccountResponse playerAccount(UUID owner) {
        Instant now = clock.instant();
        return transactions.inTransaction(connection -> {
            UUID id = accounts.ensurePlayer(connection, owner, now);
            return new PlayerAccountResponse(id, AccountKind.PLAYER.name(), owner, amounts(connection, id));
        });
    }

    /** Every currency's balance of an account, and the account's latest posting as journal position. */
    public BalancesResponse balances(UUID id) {
        return transactions.read(connection -> {
            AccountRepository.AccountRow account = accounts.find(connection, id)
                    .orElseThrow(() -> notFound(id));
            Optional<AccountRepository.PostingPosition> latest = accounts.latestPosting(connection, id);
            return new BalancesResponse(
                    id,
                    account.kind().name(),
                    latest.map(AccountRepository.PostingPosition::id).orElse(null),
                    latest.map(AccountRepository.PostingPosition::createdAt).orElse(null),
                    amounts(connection, id));
        });
    }

    /**
     * One page of an account's postings, newest first. The default page holds 50 postings, a page at most 200.
     * A page that is not the last one carries the cursor of its last posting.
     */
    public PostingsResponse postings(UUID id, Integer limit, String cursor) {
        int size = limit == null ? DEFAULT_PAGE_SIZE : limit;
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw RequestValues.invalid("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        PostingCursor after = cursor == null ? null : PostingCursor.decode(cursor);
        return transactions.read(connection -> {
            accounts.find(connection, id).orElseThrow(() -> notFound(id));
            List<AccountRepository.PostingRow> rows = accounts.postings(connection, id, after, size + 1);
            boolean more = rows.size() > size;
            List<AccountRepository.PostingRow> page = more ? rows.subList(0, size) : rows;
            List<PostingsResponse.PostingView> items = new ArrayList<>();
            for (AccountRepository.PostingRow row : page) {
                items.add(new PostingsResponse.PostingView(
                        row.id(),
                        row.transactionId(),
                        row.kind(),
                        row.reason(),
                        row.actor(),
                        AmountJson.of(row.amount()),
                        row.counterAccounts(),
                        row.createdAt()));
            }
            String next = null;
            if (more) {
                AccountRepository.PostingRow last = page.get(page.size() - 1);
                next = new PostingCursor(last.createdAt(), last.id()).encode();
            }
            return new PostingsResponse(items, next);
        });
    }

    private List<AmountJson> amounts(Connection connection, UUID accountId) throws SQLException {
        List<AmountJson> result = new ArrayList<>();
        for (AccountRepository.BalanceRow row : accounts.balances(connection, accountId)) {
            result.add(AmountJson.of(row.amount()));
        }
        return result;
    }

    public static ProblemException notFound(UUID id) {
        return ProblemException.of(ProblemCode.ACCOUNT_NOT_FOUND, "account " + id + " does not exist");
    }
}
