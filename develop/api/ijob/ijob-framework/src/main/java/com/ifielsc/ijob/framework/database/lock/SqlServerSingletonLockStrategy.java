package com.ifielsc.ijob.framework.database.lock;

import com.ifielsc.ijob.common.config.AppProperties.PlatformDatabaseVendor;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HexFormat;

import javax.sql.DataSource;

@Component
public class SqlServerSingletonLockStrategy implements SingletonLockStrategy {
    private static final String ACQUIRE_SQL =
            "{? = call sys.sp_getapplock(?, 'Exclusive', 'Session', 0, 'public')}";
    private static final String RELEASE_SQL =
            "{? = call sys.sp_releaseapplock(?, 'Session', 'public')}";
    private static final String OWNERSHIP_SQL = "SELECT APPLOCK_MODE('public', ?, 'Session')";

    private final DataSource dataSource;
    private Connection lockConnection;
    private String heldResource;

    public SqlServerSingletonLockStrategy(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public PlatformDatabaseVendor vendor() {
        return PlatformDatabaseVendor.SQLSERVER;
    }

    @Override
    public synchronized void acquire(String lockName) {
        String resource = lockResource(lockName);
        if (resource.equals(heldResource) && isHeld(lockName, 1)) {
            return;
        }
        closeHeldConnection();

        Connection candidate = null;
        try {
            candidate = dataSource.getConnection();
            try (CallableStatement statement = candidate.prepareCall(ACQUIRE_SQL)) {
                statement.registerOutParameter(1, Types.INTEGER);
                statement.setString(2, resource);
                statement.execute();
                if (statement.getInt(1) < 0) {
                    throw new IllegalStateException("Another standalone iJob process is active");
                }
            }
            lockConnection = candidate;
            heldResource = resource;
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
        String resource = lockResource(lockName);
        if (lockConnection == null || !resource.equals(heldResource)) {
            return false;
        }
        try (PreparedStatement statement = lockConnection.prepareStatement(OWNERSHIP_SQL)) {
            statement.setQueryTimeout(ownershipCheckTimeoutSeconds);
            statement.setString(1, resource);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && "Exclusive".equalsIgnoreCase(result.getString(1));
            }
        } catch (SQLException exception) {
            return false;
        }
    }

    @Override
    public synchronized void release(String lockName) {
        Connection connection = lockConnection;
        String resource = heldResource;
        lockConnection = null;
        heldResource = null;
        if (connection == null) {
            return;
        }
        try {
            if (resource != null) {
                try (CallableStatement statement = connection.prepareCall(RELEASE_SQL)) {
                    statement.registerOutParameter(1, Types.INTEGER);
                    statement.setString(2, resource);
                    statement.execute();
                }
            }
        } catch (SQLException ignored) {
            // Closing the dedicated connection also releases Session-owned application locks.
        } finally {
            close(connection);
        }
    }

    static String lockResource(String lockName) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(lockName.getBytes(StandardCharsets.UTF_8));
            return "ijob:standalone:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void closeHeldConnection() {
        Connection connection = lockConnection;
        lockConnection = null;
        heldResource = null;
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
