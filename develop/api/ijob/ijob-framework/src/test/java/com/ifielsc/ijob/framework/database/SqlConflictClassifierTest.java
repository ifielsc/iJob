// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.framework.database;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.ifielsc.ijob.common.config.AppProperties.PlatformDatabaseVendor;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;

import java.sql.SQLException;

class SqlConflictClassifierTest {
    @Test
    void sqliteShouldClassifyBusyDuplicateAndOtherConstraints() {
        SqlConflictClassifier classifier = classifier(PlatformDatabaseVendor.SQLITE);

        assertInstanceOf(
                CannotAcquireLockException.class,
                translate(
                        classifier, new SQLException("[SQLITE_BUSY] database is locked", null, 5)));
        assertInstanceOf(
                DuplicateKeyException.class,
                translate(
                        classifier,
                        new SQLException(
                                "[SQLITE_CONSTRAINT_UNIQUE] UNIQUE constraint failed", null, 19)));
        assertInstanceOf(
                DataIntegrityViolationException.class,
                translate(
                        classifier,
                        new SQLException(
                                "[SQLITE_CONSTRAINT_FOREIGNKEY] constraint failed", null, 19)));
    }

    @Test
    void serverVendorsShouldClassifyTheirStableConflictSignals() {
        assertInstanceOf(
                DuplicateKeyException.class,
                translate(
                        classifier(PlatformDatabaseVendor.MYSQL),
                        new SQLException("duplicate", "23000", 1062)));
        assertInstanceOf(
                PessimisticLockingFailureException.class,
                translate(
                        classifier(PlatformDatabaseVendor.POSTGRESQL),
                        new SQLException("deadlock", "40P01", 0)));
        assertInstanceOf(
                ConcurrencyFailureException.class,
                translate(
                        classifier(PlatformDatabaseVendor.POSTGRESQL),
                        new SQLException("serialization", "40001", 0)));
        assertInstanceOf(
                DataAccessResourceFailureException.class,
                translate(
                        classifier(PlatformDatabaseVendor.SQLSERVER),
                        new SQLException("connection", "08006", 0)));
    }

    private SqlConflictClassifier classifier(PlatformDatabaseVendor vendor) {
        return new SqlConflictClassifier(vendor);
    }

    private RuntimeException translate(SqlConflictClassifier classifier, SQLException exception) {
        return classifier.translate("test", "SELECT 1", exception);
    }
}
