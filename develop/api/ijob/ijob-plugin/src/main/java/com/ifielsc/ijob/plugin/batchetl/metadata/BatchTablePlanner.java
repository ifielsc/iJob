// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.batchetl.metadata;

import com.ifielsc.ijob.plugin.batchetl.config.BatchEtlTaskConfig;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class BatchTablePlanner {
    private final JdbcMetadataInspector inspector;

    public BatchTablePlanner(JdbcMetadataInspector inspector) {
        this.inspector = inspector;
    }

    public List<BatchTablePlan> plan(
            Connection sourceConnection, Connection targetConnection, BatchEtlTaskConfig config)
            throws SQLException {
        List<BatchTablePlan> plans = new ArrayList<>();
        Set<String> targets = new HashSet<>();
        for (String sourceTable : config.getTables()) {
            String targetTable =
                    config.getTargetNaming().getPrefix()
                            + sourceTable
                            + config.getTargetNaming().getSuffix();
            if (!targets.add(targetTable.toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Target table names must be unique");
            }
            plans.add(
                    inspector.inspect(
                            sourceConnection, targetConnection, config, sourceTable, targetTable));
        }
        return List.copyOf(plans);
    }
}
