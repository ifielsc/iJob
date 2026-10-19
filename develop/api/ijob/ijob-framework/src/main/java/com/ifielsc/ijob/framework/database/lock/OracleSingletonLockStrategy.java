// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.framework.database.lock;

import com.ifielsc.ijob.common.config.AppProperties.PlatformDatabaseVendor;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

import javax.sql.DataSource;

@Component
public class OracleSingletonLockStrategy implements SingletonLockStrategy {
    private static final String REQUEST_SQL =
            "BEGIN ? := SYS.DBMS_LOCK.REQUEST(?, 6, 0, FALSE); END;";
    private static final String RELEASE_SQL = "BEGIN ? := SYS.DBMS_LOCK.RELEASE(?); END;";

    private final DataSource dataSource;
    private Connection lockConnection;
    private Integer heldLockId;

    public OracleSingletonLockStrategy(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public PlatformDatabaseVendor vendor() {
        return PlatformDatabaseVendor.ORACLE;
    }

    @Override
    public synchronized void acquire(String lockName) {
        int requestedLockId = lockId(lockName);
        if (heldLockId != null && heldLockId == requestedLockId && isHeld(lockName, 1)) {
            return;
        }
        closeHeldConnection();

        Connection candidate = null;
        try {
            candidate = dataSource.getConnection();
            int result = request(candidate, requestedLockId, 1);
            if (result != 0 && result != 4) {
                if (result == 1) {
                    throw new IllegalStateException("Another standalone iJob process is active");
                }
                throw new IllegalStateException(
                        "Oracle DBMS_LOCK.REQUEST failed with result " + result);
            }
            lockConnection = candidate;
            heldLockId = requestedLockId;
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
        int requestedLockId = lockId(lockName);
        if (lockConnection == null || heldLockId == null || heldLockId != requestedLockId) {
            return false;
        }
        try {
            int result = request(lockConnection, requestedLockId, ownershipCheckTimeoutSeconds);
            return result == 0 || result == 4;
        } catch (SQLException exception) {
            return false;
        }
    }

    @Override
    public synchronized void release(String lockName) {
        Connection connection = lockConnection;
        Integer requestedLockId = heldLockId;
        lockConnection = null;
        heldLockId = null;
        if (connection == null) {
            return;
        }
        try {
            if (requestedLockId != null) {
                try (CallableStatement statement = connection.prepareCall(RELEASE_SQL)) {
                    statement.registerOutParameter(1, Types.INTEGER);
                    statement.setInt(2, requestedLockId);
                    statement.execute();
                }
            }
        } catch (SQLException ignored) {
            // Closing the dedicated session also releases session-owned DBMS locks.
        } finally {
            close(connection);
        }
    }

    static int lockId(String lockName) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(lockName.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getInt() & 0x3fffffff;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private int request(Connection connection, int requestedLockId, int timeoutSeconds)
            throws SQLException {
        try (CallableStatement statement = connection.prepareCall(REQUEST_SQL)) {
            statement.setQueryTimeout(timeoutSeconds);
            statement.registerOutParameter(1, Types.INTEGER);
            statement.setInt(2, requestedLockId);
            statement.execute();
            return statement.getInt(1);
        }
    }

    private void closeHeldConnection() {
        Connection connection = lockConnection;
        lockConnection = null;
        heldLockId = null;
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
