package net.onelitefeather.coloeus.ledger;

import jakarta.inject.Singleton;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * SQL for {@code idempotency_key}. A key is unique per scope ({@link IdempotencyScope}). The key row is inserted in the
 * write's database transaction and completed with the transaction id and the response before it commits.
 */
@Singleton
public class IdempotencyRepository {

    static final String UNIQUE_VIOLATION = "23505";

    /** A stored key: the hash of the request that created it and the response it was answered with. */
    public record StoredKey(String requestHash, String response) {
    }

    /**
     * Inserts the key row. A key already stored in the scope raises {@link IdempotencyKeyTaken}. The client id is null
     * for staff scopes.
     */
    void insertKey(Connection connection, String scope, UUID clientId, String key, String requestHash, Instant expiresAt)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO idempotency_key (scope, client_id, key, request_hash, expires_at) VALUES (?, ?, ?, ?, ?)")) {
            insert.setString(1, scope);
            insert.setObject(2, clientId);
            insert.setString(3, key);
            insert.setString(4, requestHash);
            insert.setTimestamp(5, Timestamp.from(expiresAt));
            insert.executeUpdate();
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                throw new IdempotencyKeyTaken(key);
            }
            throw e;
        }
    }

    void complete(Connection connection, String scope, String key, UUID transactionId, String response)
            throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE idempotency_key SET transaction_id = ?, response = ?::jsonb WHERE scope = ? AND key = ?")) {
            update.setObject(1, transactionId);
            update.setString(2, response);
            update.setString(3, scope);
            update.setString(4, key);
            update.executeUpdate();
        }
    }

    Optional<StoredKey> find(Connection connection, String scope, String key) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT request_hash, response::text AS response FROM idempotency_key WHERE scope = ? AND key = ?")) {
            select.setString(1, scope);
            select.setString(2, key);
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(new StoredKey(rows.getString("request_hash"), rows.getString("response")));
            }
        }
    }

    /** Deletes the keys that expired at or before {@code now}; returns how many were deleted. */
    int deleteExpired(Connection connection, Instant now) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM idempotency_key WHERE expires_at <= ?")) {
            delete.setTimestamp(1, Timestamp.from(now));
            return delete.executeUpdate();
        }
    }
}
