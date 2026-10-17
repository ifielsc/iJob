package com.ifelio.ijob.framework.database;

import com.ifelio.ijob.common.config.AppProperties.PlatformDatabaseVendor;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.support.SQLExceptionSubclassTranslator;
import org.springframework.jdbc.support.SQLExceptionTranslator;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;

/** Converts vendor SQL conflicts into the stable Spring data-access exception contract. */
public final class SqlConflictClassifier implements SQLExceptionTranslator {
    private static final Set<Integer> MYSQL_DUPLICATE = Set.of(1062);
    private static final Set<Integer> SQL_SERVER_DUPLICATE = Set.of(2601, 2627);
    private static final Set<Integer> MYSQL_DEADLOCK = Set.of(1213);
    private static final Set<Integer> SQL_SERVER_DEADLOCK = Set.of(1205);
    private static final Set<Integer> MYSQL_LOCK_TIMEOUT = Set.of(1205);
    private static final Set<Integer> SQL_SERVER_LOCK_TIMEOUT = Set.of(1222);
    private static final Set<Integer> ORACLE_DEADLOCK = Set.of(60);
    private static final Set<Integer> ORACLE_LOCK_TIMEOUT = Set.of(54);

    private final PlatformDatabaseVendor vendor;
    private final SQLExceptionTranslator fallback = new SQLExceptionSubclassTranslator();

    public SqlConflictClassifier(PlatformDatabaseVendor vendor) {
        this.vendor = vendor;
    }

    @Override
    public DataAccessException translate(String task, String sql, SQLException exception) {
        for (SQLException candidate = exception;
                candidate != null;
                candidate = candidate.getNextException()) {
            DataAccessException classified = classify(task, candidate);
            if (classified != null) {
                return classified;
            }
        }
        return fallback.translate(task, sql, exception);
    }

    private DataAccessException classify(String task, SQLException exception) {
        int code = exception.getErrorCode();
        String state = normalized(exception.getSQLState());
        String message = normalized(exception.getMessage());
        String description = task + "; vendor=" + vendor.name().toLowerCase(Locale.ROOT);

        if (state.startsWith("08")) {
            return new DataAccessResourceFailureException(description, exception);
        }
        if ("40P01".equals(state)
                || (vendor == PlatformDatabaseVendor.MYSQL && MYSQL_DEADLOCK.contains(code))
                || (vendor == PlatformDatabaseVendor.SQLSERVER
                        && SQL_SERVER_DEADLOCK.contains(code))
                || (vendor == PlatformDatabaseVendor.ORACLE && ORACLE_DEADLOCK.contains(code))) {
            return new PessimisticLockingFailureException(description, exception);
        }
        if ("40001".equals(state)) {
            return new ConcurrencyFailureException(description, exception);
        }
        if ("55P03".equals(state)
                || (vendor == PlatformDatabaseVendor.MYSQL && MYSQL_LOCK_TIMEOUT.contains(code))
                || (vendor == PlatformDatabaseVendor.SQLSERVER
                        && SQL_SERVER_LOCK_TIMEOUT.contains(code))
                || (vendor == PlatformDatabaseVendor.ORACLE && ORACLE_LOCK_TIMEOUT.contains(code))
                || (vendor == PlatformDatabaseVendor.SQLITE && isSqliteBusy(code, message))) {
            return new CannotAcquireLockException(description, exception);
        }
        if ("23505".equals(state)
                || (vendor == PlatformDatabaseVendor.MYSQL && MYSQL_DUPLICATE.contains(code))
                || (vendor == PlatformDatabaseVendor.SQLSERVER
                        && SQL_SERVER_DUPLICATE.contains(code))
                || (vendor == PlatformDatabaseVendor.ORACLE && code == 1)
                || (vendor == PlatformDatabaseVendor.SQLITE && isSqliteDuplicate(code, message))) {
            return new DuplicateKeyException(description, exception);
        }
        if (vendor == PlatformDatabaseVendor.SQLITE && sqlitePrimaryCode(code) == 19) {
            return new DataIntegrityViolationException(description, exception);
        }
        return null;
    }

    private boolean isSqliteBusy(int code, String message) {
        int primaryCode = sqlitePrimaryCode(code);
        return primaryCode == 5
                || primaryCode == 6
                || message.contains("SQLITE_BUSY")
                || message.contains("SQLITE_LOCKED");
    }

    private boolean isSqliteDuplicate(int code, String message) {
        return code == 1555
                || code == 2067
                || (sqlitePrimaryCode(code) == 19
                        && (message.contains("UNIQUE CONSTRAINT")
                                || message.contains("PRIMARY KEY")));
    }

    private int sqlitePrimaryCode(int code) {
        return code & 0xff;
    }

    private String normalized(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }
}
