package com.ifelio.ijob.framework.database.schema;

import com.ifelio.ijob.common.config.AppProperties.PlatformDatabaseVendor;

public interface SchemaCompatibilityInspector {
    PlatformDatabaseVendor vendor();

    void verify();
}
