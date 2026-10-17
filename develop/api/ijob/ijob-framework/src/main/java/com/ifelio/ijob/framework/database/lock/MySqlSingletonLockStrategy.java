package com.ifelio.ijob.framework.database.lock;

import com.ifelio.ijob.common.config.AppProperties.PlatformDatabaseVendor;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

@Component
public class MySqlSingletonLockStrategy implements SingletonLockStrategy {
    private final DataSource dataSource;
    private Connection lockConnection;

    public MySqlSingletonLockStrategy(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public PlatformDatabaseVendor vendor() {
        return PlatformDatabaseVendor.MYSQL;
    }

    @Override
    public synchronized void acquire(String lockName) {
        if (isHeld(lockName, 1)) {
            return;
        }
        Connection candidate = null;
        try {
            candidate = dataSource.getConnection();
            try (PreparedStatement statement =
                    candidate.prepareStatement("SELECT GET_LOCK(?, 0)")) {
                statement.setString(1, lockName);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) {
                        throw new IllegalStateException(
                                "Another standalone iJob process is active");
                    }
                }
            }
            lockConnection = candidate;
        } catch (SQLException exception) {
            close(candidate);
            throw new IllegalStateException(
                    "Failed to acquire the standalone database lock", exception);
        } catch (RuntimeException exception) {
            close(candidate);
            throw exception;
        }
    }

    @Override
    public synchronized boolean isHeld(String lockName, int ownershipCheckTimeoutSeconds) {
        if (lockConnection == null) {
            return false;
        }
        try (PreparedStatement statement =
                lockConnection.prepareStatement("SELECT IS_USED_LOCK(?) = CONNECTION_ID()")) {
            statement.setQueryTimeout(ownershipCheckTimeoutSeconds);
            statement.setString(1, lockName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        } catch (SQLException exception) {
            return false;
        }
    }

    @Override
    public synchronized void release(String lockName) {
        Connection connection = lockConnection;
        lockConnection = null;
        if (connection == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, lockName);
            statement.executeQuery();
        } catch (SQLException ignored) {
            // Closing the dedicated connection also releases a MySQL lock.
        } finally {
            close(connection);
        }
    }

    private void close(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Preserve the original startup or shutdown outcome.
        }
    }
}
