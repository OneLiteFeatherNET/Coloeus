package net.onelitefeather.coloeus.admin;

import jakarta.inject.Singleton;
import java.util.List;
import net.onelitefeather.coloeus.account.PostingCursor;
import net.onelitefeather.coloeus.persistence.JdbcTransactions;
import net.onelitefeather.coloeus.request.RequestValues;
import java.util.UUID;

/** The staff action log, newest first, one page at a time. */
@Singleton
public class StaffActionService {

    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 200;

    private final JdbcTransactions transactions;
    private final StaffQueries queries;

    public StaffActionService(JdbcTransactions transactions, StaffQueries queries) {
        this.transactions = transactions;
        this.queries = queries;
    }

    public StaffViews.StaffActionsPage list(Integer limit, String cursor) {
        int size = limit == null ? DEFAULT_PAGE_SIZE : limit;
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw RequestValues.invalid("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        PostingCursor after = cursor == null ? null : PostingCursor.decode(cursor);
        return transactions.read(connection -> {
            List<StaffViews.StaffAction> rows = queries.staffActions(connection, after, size + 1);
            boolean more = rows.size() > size;
            List<StaffViews.StaffAction> page = more ? rows.subList(0, size) : rows;
            String next = null;
            if (more) {
                StaffViews.StaffAction last = page.get(page.size() - 1);
                next = new PostingCursor(last.createdAt(), UUID.fromString(last.id())).encode();
            }
            return new StaffViews.StaffActionsPage(page, next);
        });
    }
}
