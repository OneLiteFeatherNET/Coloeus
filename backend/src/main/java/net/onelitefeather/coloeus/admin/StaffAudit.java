package net.onelitefeather.coloeus.admin;

import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes one {@code staff_action} row. Every staff write calls it inside its own database transaction, so the row
 * exists exactly when the write does (staff-operations spec).
 */
@Singleton
public class StaffAudit {

    private static final Logger LOG = LoggerFactory.getLogger(StaffAudit.class);

    private final JsonMapper json;

    public StaffAudit(JsonMapper json) {
        this.json = json;
    }

    /**
     * @param transactionId the journal transaction of a money write, null for a configuration write
     * @param detail        action specific values, for example the reason
     */
    void record(Connection connection, StaffActor actor, StaffAction action, String target, UUID transactionId,
                Map<String, String> detail, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO staff_action (id, actor_oid, actor_name, action, target, transaction_id, detail, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, actor.oid());
            statement.setString(3, actor.displayName());
            statement.setString(4, action.name());
            statement.setString(5, target);
            statement.setObject(6, transactionId);
            statement.setString(7, write(detail));
            statement.setTimestamp(8, Timestamp.from(now));
            statement.executeUpdate();
        }
        // The log carries no reason, token or cookie: the reason of a money write may name a player.
        LOG.info("staff write: action={} oid={} target={} transaction={}", action, actor.oid(), target, transactionId);
    }

    private String write(Map<String, String> detail) {
        try {
            return json.writeValueAsString(detail);
        } catch (IOException e) {
            throw new IllegalStateException("the staff action detail could not be written as JSON", e);
        }
    }

    /** The kinds of staff write that the log records (the CHECK of {@code staff_action.action}). */
    public enum StaffAction {
        RESET,
        ADJUST,
        CLIENT_REGISTERED,
        GRANTS_REPLACED,
        CURRENCY_CREATED,
        CURRENCY_DEFAULT_SET
    }
}
