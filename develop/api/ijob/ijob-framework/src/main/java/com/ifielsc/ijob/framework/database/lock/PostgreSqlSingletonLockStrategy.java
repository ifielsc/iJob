// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.framework.database.lock;

import com.ifielsc.ijob.common.config.AppProperties.PlatformDatabaseVendor;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

@Component
public class PostgreSqlSingletonLockStrategy implements SingletonLockStrategy {
    private static final String OWNERSHIP_SQL =
            "SELECT EXISTS (SELECT 1 FROM pg_locks "
                    + "WHERE locktype = 'advisory' AND pid = pg_backend_pid() AND granted)";

    private final DataSource dataSource;
    private Connection lockConnection;
    private Long heldKey;

    public PostgreSqlSingletonLockStrategy(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public PlatformDatabaseVendor vendor() {
        return PlatformDatabaseVendor.POSTGRESQL;
    }

    @Override
    public synchronized void acquire(String lockName) {
        long lockKey = lockKey(lockName);
        if (heldKey != null && heldKey == lockKey && isHeld(lockName, 1)) {
            return;
        }
        closeHeldConnection();

        Connection candidate = null;
        try {
            candidate = dataSource.getConnection();
            try (PreparedStatement statement =
                    candidate.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                statement.setLong(1, lockKey);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next() || !result.getBoolean(1)) {
                        throw new IllegalStateException(
                                "Another standalone iJob process is active");
                    }
                }
            }
            lockConnection = candidate;
            heldKey = lockKey;
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
        if (lockConnection == null || heldKey == null || heldKey.longValue() != lockKey(lockName)) {
            return false;
        }
        try (PreparedStatement statement = lockConnection.prepareStatement(OWNERSHIP_SQL)) {
            statement.setQueryTimeout(ownershipCheckTimeoutSeconds);
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
        Long lockKey = heldKey;
        lockConnection = null;
        heldKey = null;
        if (connection == null) {
            return;
        }
        try {
            if (lockKey != null) {
                try (PreparedStatement statement =
                        connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                    statement.setLong(1, lockKey);
                    statement.executeQuery();
                }
            }
        } catch (SQLException ignored) {
            // Closing the dedicated connection also releases PostgreSQL advisory locks.
        } finally {
            close(connection);
        }
    }

    static long lockKey(String lockName) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(lockName.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void closeHeldConnection() {
        Connection connection = lockConnection;
        lockConnection = null;
        heldKey = null;
        close(connection);
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
