package net.onelitefeather.coloeus.ledger;

import jakarta.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.money.CurrencyUnit;
import javax.money.MonetaryAmount;
import net.onelitefeather.coloeus.money.AmountColumns;
import org.javamoney.moneta.Money;

/**
 * SQL for the journal (transaction, posting) and the balance projection. Runs on the booking transaction.
 */
@Singleton
class JournalRepository {

    void insertTransaction(Connection connection, UUID id, TransactionCommand command, UUID clientId,
                           String metadataJson, Instant now) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO transaction (id, kind, reason, actor, external_reference, client_id, metadata, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)")) {
            insert.setObject(1, id);
            insert.setString(2, command.kind().name());
            insert.setString(3, command.reason());
            insert.setString(4, command.actor());
            insert.setString(5, command.externalReference());
            insert.setObject(6, clientId);
            insert.setString(7, metadataJson);
            insert.setTimestamp(8, Timestamp.from(now));
            insert.executeUpdate();
        }
    }

    void insertPosting(Connection connection, UUID transactionId, UUID accountId, MonetaryAmount amount, Instant now)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO posting (id, transaction_id, account_id, currency, amount, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, transactionId);
            insert.setObject(3, accountId);
            insert.setString(4, amount.getCurrency().getCurrencyCode());
            insert.setBigDecimal(5, AmountColumns.toColumn(amount));
            insert.setTimestamp(6, Timestamp.from(now));
            insert.executeUpdate();
        }
    }

    void ensureBalance(Connection connection, UUID accountId, String currency, Instant now) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO balance (account_id, currency, amount, updated_at) VALUES (?, ?, 0, ?) "
                        + "ON CONFLICT DO NOTHING")) {
            insert.setObject(1, accountId);
            insert.setString(2, currency);
            insert.setTimestamp(3, Timestamp.from(now));
            insert.executeUpdate();
        }
    }

    /** Reads a balance and keeps its row locked until the transaction ends. */
    Money lockBalance(Connection connection, UUID accountId, String currency, CurrencyUnit unit) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT amount FROM balance WHERE account_id = ? AND currency = ? FOR UPDATE")) {
            select.setObject(1, accountId);
            select.setString(2, currency);
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException("balance row of " + accountId + " in " + currency + " is missing");
                }
                return AmountColumns.fromColumn(rows.getBigDecimal("amount"), unit);
            }
        }
    }

    void setBalance(Connection connection, UUID accountId, String currency, MonetaryAmount amount, Instant now)
            throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE balance SET amount = ?, updated_at = ? WHERE account_id = ? AND currency = ?")) {
            update.setBigDecimal(1, AmountColumns.toColumn(amount));
            update.setTimestamp(2, Timestamp.from(now));
            update.setObject(3, accountId);
            update.setString(4, currency);
            update.executeUpdate();
        }
    }

    /** Adds a signed delta without a prior lock; used for accounts that may go negative and never gate a booking. */
    void addToBalance(Connection connection, UUID accountId, String currency, MonetaryAmount delta, Instant now)
            throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE balance SET amount = amount + ?, updated_at = ? WHERE account_id = ? AND currency = ?")) {
            update.setBigDecimal(1, AmountColumns.toColumn(delta));
            update.setTimestamp(2, Timestamp.from(now));
            update.setObject(3, accountId);
            update.setString(4, currency);
            update.executeUpdate();
        }
    }
}
