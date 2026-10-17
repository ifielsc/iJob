package com.ifelio.ijob.framework.database.lock;

import com.ifelio.ijob.common.config.AppProperties.PlatformDatabaseVendor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Component
public class SqliteSingletonLockStrategy implements SingletonLockStrategy {
    private final String jdbcUrl;
    private Path databasePath;
    private Path lockPath;
    private FileChannel lockChannel;
    private FileLock fileLock;
    private String heldName;

    public SqliteSingletonLockStrategy(@Value("${spring.datasource.url:}") String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    @Override
    public PlatformDatabaseVendor vendor() {
        return PlatformDatabaseVendor.SQLITE;
    }

    @Override
    public synchronized void acquire(String lockName) {
        if (lockName.equals(heldName) && isHeld(lockName, 1)) {
            return;
        }
        release(heldName);
        databasePath = SqliteDatabasePath.fromJdbcUrl(jdbcUrl);
        lockPath = SqliteDatabasePath.lockFile(databasePath);
        try {
            FileChannel candidate =
                    FileChannel.open(
                            lockPath,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.READ,
                            StandardOpenOption.WRITE);
            FileLock candidateLock;
            try {
                candidateLock = candidate.tryLock();
            } catch (OverlappingFileLockException exception) {
                candidate.close();
                throw activeProcess(exception);
            }
            if (candidateLock == null) {
                candidate.close();
                throw activeProcess(null);
            }
            lockChannel = candidate;
            fileLock = candidateLock;
            heldName = lockName;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to acquire the SQLite standalone file lock for " + databasePath,
                    exception);
        }
    }

    @Override
    public synchronized boolean isHeld(String lockName, int ownershipCheckTimeoutSeconds) {
        return lockName != null
                && lockName.equals(heldName)
                && fileLock != null
                && fileLock.isValid()
                && lockChannel != null
                && lockChannel.isOpen();
    }

    @Override
    public synchronized void release(String lockName) {
        FileLock currentLock = fileLock;
        FileChannel currentChannel = lockChannel;
        fileLock = null;
        lockChannel = null;
        heldName = null;
        try {
            if (currentLock != null && currentLock.isValid()) {
                currentLock.release();
            }
        } catch (IOException ignored) {
            // Closing the channel below also releases the operating-system file lock.
        } finally {
            if (currentChannel != null) {
                try {
                    currentChannel.close();
                } catch (IOException ignored) {
                    // Preserve the original startup or shutdown outcome.
                }
            }
        }
    }

    public Path databasePath() {
        return databasePath == null ? SqliteDatabasePath.fromJdbcUrl(jdbcUrl) : databasePath;
    }

    public Path lockPath() {
        return lockPath == null ? SqliteDatabasePath.lockFile(databasePath()) : lockPath;
    }

    private IllegalStateException activeProcess(Throwable cause) {
        return new IllegalStateException(
                "Another standalone iJob process is using SQLite database " + databasePath, cause);
    }
}
