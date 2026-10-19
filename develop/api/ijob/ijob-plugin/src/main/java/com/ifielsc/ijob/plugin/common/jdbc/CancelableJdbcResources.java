// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.common.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns short-lived JDBC resources and lets task cleanup cancel them safely. */
public final class CancelableJdbcResources implements AutoCloseable {
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Connection connection;
    private volatile Statement statement;
    private volatile ResultSet resultSet;

    public void attach(Connection opened) throws SQLException {
        connection = opened;
        closeIfStopped(opened);
    }

    public void attach(Statement opened) throws SQLException {
        statement = opened;
        if (closed.get()) {
            cancelQuietly(opened);
            closeQuietly(opened);
            throw stopped();
        }
    }

    public void attach(ResultSet opened) throws SQLException {
        resultSet = opened;
        closeIfStopped(opened);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        cancelQuietly(statement);
        closeQuietly(resultSet);
        closeQuietly(statement);
        closeQuietly(connection);
    }

    private void closeIfStopped(AutoCloseable opened) throws SQLException {
        if (closed.get()) {
            closeQuietly(opened);
            throw stopped();
        }
    }

    private SQLException stopped() {
        return new SQLException("JDBC operation was stopped");
    }

    private static void cancelQuietly(Statement value) {
        if (value != null) {
            try {
                value.cancel();
            } catch (Exception ignored) {
                // Closing remains the fallback.
            }
        }
    }

    private static void closeQuietly(AutoCloseable value) {
        if (value != null) {
            try {
                value.close();
            } catch (Exception ignored) {
                // Preserve the primary query or stop outcome.
            }
        }
    }
}
