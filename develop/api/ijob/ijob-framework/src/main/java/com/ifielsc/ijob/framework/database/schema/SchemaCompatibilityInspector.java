// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.framework.database.schema;

import com.ifielsc.ijob.common.config.AppProperties.PlatformDatabaseVendor;

public interface SchemaCompatibilityInspector {
    PlatformDatabaseVendor vendor();

    void verify();
}
