// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.PlatformDatabaseVendor;
import com.ifielsc.ijob.framework.database.schema.SchemaCompatibilityInspector;

import org.junit.jupiter.api.Test;

import java.util.List;

class DatabaseSchemaVersionGuardTest {
    @Test
    void shouldDelegateToConfiguredVendorInspector() {
        AppProperties properties = new AppProperties();
        TrackingInspector mysql = new TrackingInspector(PlatformDatabaseVendor.MYSQL);
        TrackingInspector oracle = new TrackingInspector(PlatformDatabaseVendor.ORACLE);
        DatabaseSchemaVersionGuard guard =
                new DatabaseSchemaVersionGuard(List.of(oracle, mysql), properties);

        guard.verify();

        assertEquals(1, mysql.invocations);
        assertEquals(0, oracle.invocations);
    }

    private static class TrackingInspector implements SchemaCompatibilityInspector {
        private final PlatformDatabaseVendor vendor;
        private int invocations;

        private TrackingInspector(PlatformDatabaseVendor vendor) {
            this.vendor = vendor;
        }

        @Override
        public PlatformDatabaseVendor vendor() {
            return vendor;
        }

        @Override
        public void verify() {
            invocations++;
        }
    }
}
