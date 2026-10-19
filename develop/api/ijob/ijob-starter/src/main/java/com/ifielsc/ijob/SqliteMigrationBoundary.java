package com.ifielsc.ijob;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.BaseCallback;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;

/** Keeps published SQLite table-rebuild migrations safe without rewriting their checksums. */
final class SqliteMigrationBoundary {
    private SqliteMigrationBoundary() {}

    static void migrate(Flyway original) {
        // Lease exactly one connection, so disabling FK enforcement cannot affect other borrowers.
        try (Connection connection = original.getConfiguration().getDataSource().getConnection()) {
            if (!connection.getAutoCommit()) {
                throw new FlywayException("SQLite migrations require an auto-commit connection");
            }
            int foreignKeys = pragma(connection, "foreign_keys");
            int legacyAlter = pragma(connection, "legacy_alter_table");
            int deferred = pragma(connection, "defer_foreign_keys");
            try {
                verifyForeignKeys(connection);
                setPragma(connection, "foreign_keys", 0);
                var callbacks =
                        new ArrayList<Callback>(
                                Arrays.asList(original.getConfiguration().getCallbacks()));
                callbacks.add(new ForeignKeyValidation());
                Flyway.configure()
                        .configuration(original.getConfiguration())
                        .dataSource(new SingleConnectionDataSource(connection, true))
                        .callbacks(callbacks.toArray(Callback[]::new))
                        .load()
                        .migrate();
                verifyForeignKeys(connection);
            } finally {
                try {
                    if (!connection.getAutoCommit()) {
                        connection.rollback();
                        connection.setAutoCommit(true);
                    }
                    setPragma(connection, "legacy_alter_table", legacyAlter);
                    setPragma(connection, "defer_foreign_keys", deferred);
                    setPragma(connection, "foreign_keys", foreignKeys);
                } catch (SQLException restoreFailure) {
                    // Never return a connection with unknown FK state to the application pool.
                    try {
                        connection.abort(Runnable::run);
                    } catch (SQLException abortFailure) {
                        restoreFailure.addSuppressed(abortFailure);
                    }
                    throw restoreFailure;
                }
            }
        } catch (SQLException exception) {
            throw new FlywayException(
                    "SQLite migration connection could not be restored", exception);
        }
    }

    private static int pragma(Connection connection, String name) throws SQLException {
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("PRAGMA " + name)) {
            if (!rows.next()) throw new SQLException("SQLite pragma is unavailable");
            return rows.getInt(1);
        }
    }

    private static void setPragma(Connection connection, String name, int value)
            throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA " + name + " = " + value);
        }
        if (pragma(connection, name) != value)
            throw new SQLException("SQLite pragma could not be restored");
    }

    private static void verifyForeignKeys(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("PRAGMA foreign_key_check")) {
            if (rows.next())
                throw new FlywayException("SQLite migration violates foreign key integrity");
        }
    }

    private static final class ForeignKeyValidation extends BaseCallback {
        @Override
        public boolean supports(Event event, Context context) {
            return event == Event.AFTER_EACH_MIGRATE;
        }

        @Override
        public boolean canHandleInTransaction(Event event, Context context) {
            return true;
        }

        @Override
        public void handle(Event event, Context context) {
            try {
                verifyForeignKeys(context.getConnection());
            } catch (SQLException exception) {
                throw new FlywayException("SQLite foreign key validation failed", exception);
            }
        }
    }
}
