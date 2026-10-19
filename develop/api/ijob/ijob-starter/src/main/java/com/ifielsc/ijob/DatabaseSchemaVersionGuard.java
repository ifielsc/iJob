// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.PlatformDatabaseVendor;
import com.ifielsc.ijob.framework.database.schema.SchemaCompatibilityInspector;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatabaseSchemaVersionGuard {
    private final List<SchemaCompatibilityInspector> inspectors;
    private final AppProperties appProperties;

    public DatabaseSchemaVersionGuard(
            List<SchemaCompatibilityInspector> inspectors, AppProperties appProperties) {
        this.inspectors = inspectors;
        this.appProperties = appProperties;
    }

    public void verify() {
        PlatformDatabaseVendor vendor = appProperties.getPlatformDatabase().getVendor();
        inspectors.stream()
                .filter(inspector -> inspector.vendor() == vendor)
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "No schema compatibility inspector is available for "
                                                + vendor))
                .verify();
    }
}
