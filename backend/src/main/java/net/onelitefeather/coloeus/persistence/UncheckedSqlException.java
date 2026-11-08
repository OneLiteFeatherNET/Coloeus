package net.onelitefeather.coloeus.persistence;

import java.sql.SQLException;

/**
 * A database failure that is not part of the request contract. Keeps the SQL state for the few cases that map to
 * a problem (for example unique violations).
 */
public class UncheckedSqlException extends RuntimeException {

    public static final String UNIQUE_VIOLATION = "23505";

    private final String sqlState;

    public UncheckedSqlException(SQLException cause) {
        super(cause.getMessage(), cause);
        this.sqlState = cause.getSQLState();
    }

    public String sqlState() {
        return sqlState;
    }

    public boolean isState(String state) {
        return state.equals(sqlState);
    }
}
