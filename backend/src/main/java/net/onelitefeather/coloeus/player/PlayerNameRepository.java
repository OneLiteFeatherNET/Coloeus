package net.onelitefeather.coloeus.player;

import jakarta.inject.Singleton;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SQL for the {@code player_name} register. Every method runs on the connection of the caller's transaction.
 */
@Singleton
public class PlayerNameRepository {

    /** Records a sighting of a name for a UUID: a new row, or the existing one with a newer last-seen time. */
    void record(Connection connection, UUID uuid, String name, NameSource source, Instant seenAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO player_name (uuid, name, first_seen, last_seen, source) VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT (uuid, name) DO UPDATE SET last_seen = EXCLUDED.last_seen, source = EXCLUDED.source")) {
            Timestamp now = Timestamp.from(seenAt);
            statement.setObject(1, uuid);
            statement.setString(2, name);
            statement.setTimestamp(3, now);
            statement.setTimestamp(4, now);
            statement.setString(5, source.name());
            statement.executeUpdate();
        }
    }

    /** The entry of one UUID and name. */
    Optional<PlayerNameView> find(Connection connection, UUID uuid, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT uuid, name, first_seen, last_seen, source FROM player_name WHERE uuid = ? AND name = ?")) {
            statement.setObject(1, uuid);
            statement.setString(2, name);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(view(rows)) : Optional.empty();
            }
        }
    }

    /**
     * The UUID that most recently held the name, case-insensitively. Empty when the register does not know the name.
     */
    Optional<PlayerNameView> newestHolder(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT uuid, name, first_seen, last_seen, source FROM player_name "
                        + "WHERE lower(name) = lower(?) ORDER BY last_seen DESC, uuid LIMIT 1")) {
            statement.setString(1, name);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(view(rows)) : Optional.empty();
            }
        }
    }

    private static PlayerNameView view(ResultSet rows) throws SQLException {
        return new PlayerNameView(
                rows.getObject("uuid", UUID.class),
                rows.getString("name"),
                rows.getTimestamp("first_seen").toInstant(),
                rows.getTimestamp("last_seen").toInstant(),
                NameSource.valueOf(rows.getString("source")));
    }

    /** All names a UUID has held, most recent first. */
    List<NameEntry> history(Connection connection, UUID uuid) throws SQLException {
        List<NameEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT name, first_seen, last_seen, source FROM player_name WHERE uuid = ? ORDER BY last_seen DESC, name")) {
            statement.setObject(1, uuid);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    entries.add(new NameEntry(
                            rows.getString("name"),
                            rows.getTimestamp("first_seen").toInstant(),
                            rows.getTimestamp("last_seen").toInstant(),
                            NameSource.valueOf(rows.getString("source"))));
                }
            }
        }
        return entries;
    }
}
