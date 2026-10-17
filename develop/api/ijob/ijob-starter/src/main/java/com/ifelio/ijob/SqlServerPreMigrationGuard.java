package com.ifelio.ijob;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

public class SqlServerPreMigrationGuard {
    private static final String RCSI_SQL =
            "SELECT is_read_committed_snapshot_on FROM sys.databases WHERE database_id = DB_ID()";

    public void verify(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(RCSI_SQL);
                ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new IllegalStateException(
                        "Unable to verify SQL Server READ_COMMITTED_SNAPSHOT mode");
            }
            if (result.getBoolean(1)) {
                throw new IllegalStateException(
                        "SQL Server READ_COMMITTED_SNAPSHOT must be OFF for the V19 work queue");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to verify SQL Server READ_COMMITTED_SNAPSHOT mode", exception);
        }
    }
}
