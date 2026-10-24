// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.contract.task.EtlInspectionDTO;
import com.ifielsc.ijob.contract.task.EtlInspectionDTO.Checkpoint;
import com.ifielsc.ijob.core.etl.EtlInspection;
import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.core.task.TaskService;
import com.ifielsc.ijob.infra.entity.runtime.EtlCheckpoint;
import com.ifielsc.ijob.infra.mapper.runtime.EtlCheckpointMapper;
import com.ifielsc.ijob.plugin.batchetl.BatchTableMigrator;
import com.ifielsc.ijob.plugin.batchetl.config.BatchEtlTaskConfig;
import com.ifielsc.ijob.plugin.batchetl.metadata.JdbcMetadataInspector;
import com.ifielsc.ijob.plugin.common.config.PluginConfigCodec;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifielsc.ijob.plugin.etl.incremental.EtlIncrementalCoordinator;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class EtlInspectionService implements EtlInspection {
    private final TaskService tasks;
    private final EtlCheckpointMapper checkpoints;
    private final ResourceResolver resources;
    private final PluginConfigCodec codec;
    private final ObjectMapper json;
    private final JdbcMetadataInspector inspector;

    @Override
    public EtlInspectionDTO.Progress progress(Long taskId) {
        var task = tasks.getDetail(taskId);
        List<Checkpoint> rows = new ArrayList<>();
        String mode = "DISABLED";
        if (task.getPluginType() == PluginType.ETL) {
            var config = codec.read(task.getPluginConfig(), EtlTaskConfig.class, "ETL");
            var inc = config.getIncremental();
            if (inc != null
                    && inc.isEnabled()
                    && config.getSource().getType() == EtlTaskConfig.SourceType.DATABASE) {
                mode = "INCREMENTAL";
                String fingerprint =
                        task.getPluginConfig().contains("${")
                                ? null
                                : EtlIncrementalCoordinator.fingerprint(config);
                rows.add(
                        row(
                                "SQL",
                                "",
                                inc.getCheckpointKey(),
                                inc.getWatermarkField(),
                                inc.getWatermarkType(),
                                inc.getInitialWatermark(),
                                checkpoints.select(taskId, inc.getCheckpointKey()),
                                fingerprint));
            } else if (inc != null && inc.isEnabled()) {
                mode = "OTHER_SOURCE";
            }
        } else if (task.getPluginType() == PluginType.BATCH_ETL) {
            var config = codec.read(task.getPluginConfig(), BatchEtlTaskConfig.class, "Batch ETL");
            if (config.getExtractMode() == BatchEtlTaskConfig.ExtractMode.INCREMENTAL) {
                mode = "INCREMENTAL";
                var stored = checkpoints.selectByTask(taskId);
                // Metadata is optional evidence. Never execute extraction SQL or initialize
                // checkpoints.
                Connection source = null;
                Connection target = null;
                try {
                    if (!task.getPluginConfig().contains("${")) {
                        source =
                                resources
                                        .getDataSource(config.getSourceResourceId())
                                        .getConnection();
                        target =
                                resources
                                        .getDataSource(config.getTargetResourceId())
                                        .getConnection();
                    }
                    for (String table : config.getTables()) {
                        String name =
                                config.getTargetNaming().getPrefix()
                                        + table
                                        + config.getTargetNaming().getSuffix();
                        String key =
                                BatchTableMigrator.checkpointKeyFor(
                                        config.getSourceSchema(), table);
                        String fingerprint = null;
                        if (source != null && target != null) {
                            try {
                                var plan = inspector.inspect(source, target, config, table, name);
                                key = BatchTableMigrator.checkpointKey(config, plan);
                                fingerprint = BatchTableMigrator.fingerprint(config, plan);
                            } catch (SQLException | IllegalArgumentException ignored) {
                                // Keep saved values visible when schema inspection cannot verify
                                // the binding.
                            }
                        }
                        final String resolvedKey = key;
                        var saved =
                                stored.stream()
                                        .filter(c -> resolvedKey.equals(c.getCheckpointKey()))
                                        .findFirst()
                                        .orElse(null);
                        rows.add(
                                row(
                                        table,
                                        name,
                                        key,
                                        config.getIncremental().getWatermarkColumn(),
                                        config.getIncremental().getWatermarkType(),
                                        config.getIncremental().getInitialWatermark(),
                                        saved,
                                        fingerprint));
                    }
                } catch (SQLException | BusinessException failure) {
                    for (String table : config.getTables()) {
                        String key =
                                BatchTableMigrator.checkpointKeyFor(
                                        config.getSourceSchema(), table);
                        var saved =
                                stored.stream()
                                        .filter(c -> key.equals(c.getCheckpointKey()))
                                        .findFirst()
                                        .orElse(null);
                        rows.add(
                                row(
                                        table,
                                        config.getTargetNaming().getPrefix()
                                                + table
                                                + config.getTargetNaming().getSuffix(),
                                        key,
                                        config.getIncremental().getWatermarkColumn(),
                                        config.getIncremental().getWatermarkType(),
                                        config.getIncremental().getInitialWatermark(),
                                        saved,
                                        null));
                    }
                } finally {
                    close(target);
                    close(source);
                }
                // Retain checkpoints whose table identity cannot be resolved, including
                // renamed/removed tables.
                for (var saved : stored) {
                    if (rows.stream()
                            .noneMatch(
                                    r ->
                                            r.checkpointKey().equals(saved.getCheckpointKey())
                                                    && r.version() != null))
                        rows.add(
                                row(
                                        "",
                                        "",
                                        saved.getCheckpointKey(),
                                        "",
                                        saved.getWatermarkType(),
                                        null,
                                        saved,
                                        null));
                }
            }
        } else {
            throw invalid();
        }
        return new EtlInspectionDTO.Progress(
                String.valueOf(task.getCurrentRevision()), mode, List.copyOf(rows));
    }

    private Checkpoint row(
            String source,
            String target,
            String key,
            String field,
            String type,
            Object initial,
            EtlCheckpoint saved,
            String fingerprint) {
        String match =
                saved == null
                        ? "NONE"
                        : fingerprint == null
                                ? "UNVERIFIED"
                                : Objects.equals(fingerprint, saved.getSourceFingerprint())
                                                && Objects.equals(type, saved.getWatermarkType())
                                        ? "MATCH"
                                        : "CHANGED";
        return new Checkpoint(
                source,
                target,
                key,
                field,
                type,
                encoded(initial),
                saved == null ? null : saved.getWatermarkValue(),
                saved == null ? null : saved.getTieBreakerValue(),
                saved == null ? null : String.valueOf(saved.getVersion()),
                saved == null || saved.getLastInstanceId() == null
                        ? null
                        : String.valueOf(saved.getLastInstanceId()),
                saved == null ? null : saved.getUpdateTime(),
                saved == null ? "EMPTY" : saved.getVersion() == 0 ? "INITIALIZED" : "ADVANCED",
                match);
    }

    @Override
    public EtlInspectionDTO.Tables tables(Long resourceId, String schema, boolean includeViews) {
        if (resourceId == null
                || resourceId <= 0
                || schema == null
                || schema.length() > 128
                || schema.contains("${")) throw invalid();
        try (Connection connection = resources.getDataSource(resourceId).getConnection()) {
            DatabaseMetaData md = connection.getMetaData();
            String product = md.getDatabaseProductName().toLowerCase(Locale.ROOT);
            boolean catalogBased = product.contains("mysql") || product.contains("mariadb");
            String scope =
                    schema.isBlank()
                            ? (catalogBased
                                    ? connection.getCatalog()
                                    : product.contains("sqlite") ? null : connection.getSchema())
                            : schema;
            if (!catalogBased && (scope == null || scope.isBlank()) && !product.contains("sqlite"))
                throw invalid();
            String catalog = catalogBased ? scope : connection.getCatalog();
            String pattern = catalogBased ? null : scope;
            List<EtlInspectionDTO.Table> rows = new ArrayList<>();
            boolean truncated = false;
            try (ResultSet result =
                    md.getTables(
                            catalog,
                            pattern,
                            "%",
                            includeViews
                                    ? new String[] {"TABLE", "VIEW"}
                                    : new String[] {"TABLE"})) {
                while (result.next()) {
                    String actual = result.getString(catalogBased ? "TABLE_CAT" : "TABLE_SCHEM");
                    if (scope != null && actual != null && !scope.equals(actual)) continue;
                    if (rows.size() == 10000) {
                        truncated = true;
                        break;
                    }
                    rows.add(
                            new EtlInspectionDTO.Table(
                                    result.getString("TABLE_NAME"),
                                    result.getString("TABLE_TYPE")));
                }
            }
            rows.sort(Comparator.comparing(EtlInspectionDTO.Table::name));
            return new EtlInspectionDTO.Tables(scope, List.copyOf(rows), truncated);
        } catch (SQLException exception) {
            throw BusinessException.localized(
                    ResultCode.SERVICE_UNAVAILABLE, "Database metadata is unavailable");
        }
    }

    @Override
    public EtlInspectionDTO.Checks check(String pluginConfig) {
        if (pluginConfig == null || pluginConfig.length() > 262144 || pluginConfig.contains("${"))
            throw invalid();
        var config = codec.read(pluginConfig, BatchEtlTaskConfig.class, "Batch ETL");
        if (config.getSourceResourceId() == null
                || config.getTargetResourceId() == null
                || config.getTables() == null
                || config.getTables().isEmpty()
                || config.getTables().size() > 500
                || config.getTables().stream()
                        .anyMatch(t -> t == null || t.isBlank() || t.length() > 128)
                || config.getTargetNaming() == null
                || config.getIncremental() == null
                || config.getLogicalKeys() == null
                || config.getLoadMode() == null
                || config.getExtractMode() == null
                || config.getLogicalKeys().stream().anyMatch(k -> k == null || k.isBlank())
                || (config.getLoadMode() == BatchEtlTaskConfig.LoadMode.UPSERT
                                || config.getLoadMode() == BatchEtlTaskConfig.LoadMode.UPDATE_ONLY)
                        && config.getLogicalKeys().isEmpty()
                || config.getExtractMode() == BatchEtlTaskConfig.ExtractMode.INCREMENTAL
                        && (config.getIncremental().getWatermarkColumn() == null
                                || config.getIncremental().getWatermarkColumn().isBlank()))
            throw invalid();
        List<EtlInspectionDTO.Check> rows = new ArrayList<>();
        try (Connection source =
                        resources.getDataSource(config.getSourceResourceId()).getConnection();
                Connection target =
                        resources.getDataSource(config.getTargetResourceId()).getConnection()) {
            for (String table : config.getTables()) {
                String name =
                        config.getTargetNaming().getPrefix()
                                + table
                                + config.getTargetNaming().getSuffix();
                try {
                    var plan = inspector.inspect(source, target, config, table, name);
                    boolean ignored =
                            inspector.hasUnmappedSourceColumns(
                                    source, config.getSourceSchema(), table, plan.columns().size());
                    rows.add(
                            new EtlInspectionDTO.Check(
                                    table,
                                    name,
                                    ignored ? "WARNING" : "PASS",
                                    ignored ? "UNMAPPED_COLUMNS" : "COMPATIBLE"));
                } catch (IllegalArgumentException exception) {
                    rows.add(
                            new EtlInspectionDTO.Check(
                                    table, name, "FAIL", exception.getMessage()));
                }
            }
        } catch (SQLException exception) {
            throw BusinessException.localized(
                    ResultCode.SERVICE_UNAVAILABLE, "Database metadata is unavailable");
        }
        return new EtlInspectionDTO.Checks(List.copyOf(rows));
    }

    private String encoded(Object value) {
        try {
            return value == null ? null : json.writeValueAsString(value);
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private static void close(Connection connection) {
        if (connection != null)
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
    }

    private static BusinessException invalid() {
        return BusinessException.localized(
                ResultCode.PARAM_ERROR, "Invalid ETL inspection configuration");
    }
}
