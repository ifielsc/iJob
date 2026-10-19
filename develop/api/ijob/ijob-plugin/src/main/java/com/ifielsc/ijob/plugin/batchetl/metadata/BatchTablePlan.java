// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.batchetl.metadata;

import java.util.List;

public record BatchTablePlan(
        String sourceSchema,
        String sourceTable,
        String targetSchema,
        String targetTable,
        List<ColumnMapping> columns,
        List<ColumnMapping> logicalKeys,
        ColumnMapping watermark) {
    public record ColumnMapping(
            String sourceColumn, String targetColumn, int sourceJdbcType, int targetJdbcType) {}
}
