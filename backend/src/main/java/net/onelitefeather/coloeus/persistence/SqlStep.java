package net.onelitefeather.coloeus.persistence;

import java.sql.Connection;
import java.sql.SQLException;

/** A step of plain JDBC work that returns nothing, run on the connection of a transaction. */
@FunctionalInterface
public interface SqlStep {

    void run(Connection connection) throws SQLException;
}
