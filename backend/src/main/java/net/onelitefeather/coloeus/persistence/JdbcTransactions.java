package net.onelitefeather.coloeus.persistence;

import io.micronaut.data.connection.annotation.Connectable;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Runs plain JDBC work in one database transaction on a pooled connection. Committed on return, rolled back on
 * any exception.
 */
@Singleton
public class JdbcTransactions {

    private final DataSource dataSource;

    public JdbcTransactions(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Connectable
    public <T> T inTransaction(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException sql) {
                connection.rollback();
                throw new UncheckedSqlException(sql);
            } catch (RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    @Connectable
    public <T> T read(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            return work.apply(connection);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    @FunctionalInterface
    public interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }
}
