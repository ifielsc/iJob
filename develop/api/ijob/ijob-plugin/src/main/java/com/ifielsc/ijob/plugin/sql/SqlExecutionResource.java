package com.ifielsc.ijob.plugin.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

final class SqlExecutionResource implements AutoCloseable {
    private final Connection connection;
    private final boolean transactional;
    private Statement statement;
    private boolean closed;
    private boolean transactionComplete;

    SqlExecutionResource(Connection connection, boolean transactional) {
        this.connection = connection;
        this.transactional = transactional;
    }

    void bindStatement(Statement candidate) throws SQLException {
        synchronized (this) {
            if (!closed) {
                statement = candidate;
                return;
            }
        }
        cancelAndClose(candidate);
        throw new SQLException("SQL execution has been stopped");
    }

    synchronized void clearStatement() {
        statement = null;
    }

    synchronized void markTransactionComplete() {
        transactionComplete = true;
    }

    synchronized void rollbackTransaction() throws SQLException {
        if (!transactional || transactionComplete) {
            return;
        }
        if (!connection.getAutoCommit()) {
            connection.rollback();
        }
        transactionComplete = true;
    }

    @Override
    public void close() {
        Statement activeStatement;
        boolean rollbackRequired;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            activeStatement = statement;
            statement = null;
            rollbackRequired = transactional && !transactionComplete;
        }
        cancelAndClose(activeStatement);
        if (rollbackRequired) {
            try {
                rollbackTransaction();
            } catch (SQLException ignored) {
            }
        }
        closeConnection();
    }

    private void cancelAndClose(Statement candidate) {
        if (candidate == null) {
            return;
        }
        try {
            candidate.cancel();
        } catch (SQLException ignored) {
        }
        try {
            candidate.close();
        } catch (SQLException ignored) {
        }
    }

    private void closeConnection() {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
