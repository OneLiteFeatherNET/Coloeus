package net.onelitefeather.coloeus.auth;

import jakarta.inject.Singleton;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * SQL for the {@code client} and {@code client_grant} tables. Every method runs on the connection of the caller's
 * transaction.
 */
@Singleton
public class ClientRepository {

    /** Locks the client row until the transaction ends; false when no such client is registered. */
    boolean lockClient(Connection connection, UUID clientId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM client WHERE client_id = ? FOR UPDATE")) {
            statement.setObject(1, clientId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    boolean exists(Connection connection, UUID clientId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM client WHERE client_id = ?")) {
            statement.setObject(1, clientId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    void insert(Connection connection, UUID clientId, String displayName, Instant createdAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO client (client_id, display_name, created_at) VALUES (?, ?, ?)")) {
            statement.setObject(1, clientId);
            statement.setString(2, displayName);
            statement.setTimestamp(3, Timestamp.from(createdAt));
            statement.executeUpdate();
        }
    }

    Optional<ClientRecord> find(Connection connection, UUID clientId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT client_id, display_name, created_at FROM client WHERE client_id = ?")) {
            statement.setObject(1, clientId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ClientRecord(
                        rows.getObject("client_id", UUID.class),
                        rows.getString("display_name"),
                        rows.getTimestamp("created_at").toInstant()));
            }
        }
    }

    List<ClientRecord> findAll(Connection connection) throws SQLException {
        List<ClientRecord> clients = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT client_id, display_name, created_at FROM client ORDER BY display_name, client_id");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                clients.add(new ClientRecord(
                        rows.getObject("client_id", UUID.class),
                        rows.getString("display_name"),
                        rows.getTimestamp("created_at").toInstant()));
            }
        }
        return clients;
    }

    List<GrantView> findGrants(Connection connection, UUID clientId) throws SQLException {
        List<GrantView> grants = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT currency, permission FROM client_grant WHERE client_id = ? ORDER BY currency, permission")) {
            statement.setObject(1, clientId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    grants.add(new GrantView(rows.getString("currency"),
                            Permission.valueOf(rows.getString("permission"))));
                }
            }
        }
        return grants;
    }

    boolean hasAnyPermission(Connection connection, UUID clientId, Permission permission) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM client_grant WHERE client_id = ? AND permission = ?")) {
            statement.setObject(1, clientId);
            statement.setString(2, permission.name());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    Set<Permission> permissions(Connection connection, UUID clientId, String currency) throws SQLException {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT permission FROM client_grant WHERE client_id = ? AND currency = ?")) {
            statement.setObject(1, clientId);
            statement.setString(2, currency);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    permissions.add(Permission.valueOf(rows.getString("permission")));
                }
            }
        }
        return permissions;
    }

    boolean currencyExists(Connection connection, String code) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM currency WHERE code = ?")) {
            statement.setString(1, code);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    void deleteGrants(Connection connection, UUID clientId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM client_grant WHERE client_id = ?")) {
            statement.setObject(1, clientId);
            statement.executeUpdate();
        }
    }

    void insertGrant(Connection connection, UUID clientId, GrantView grant) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO client_grant (client_id, currency, permission) VALUES (?, ?, ?)")) {
            statement.setObject(1, clientId);
            statement.setString(2, grant.currency());
            statement.setString(3, grant.permission().name());
            statement.executeUpdate();
        }
    }

    /** A registered client as stored. */
    record ClientRecord(UUID clientId, String displayName, Instant createdAt) {
    }
}
