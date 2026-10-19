// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.config;

import com.ifielsc.ijob.plugin.etl.incremental.EtlWatermark;
import com.ifielsc.ijob.plugin.etl.incremental.FileMarkerSelector;
import com.ifielsc.ijob.plugin.etl.transformer.TransformType;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

public final class FileIncrementalConfigValidator {
    private FileIncrementalConfigValidator() {}

    public static void validate(EtlTaskConfig config) {
        var incremental = config.getIncremental();
        var file = incremental.getFile();
        require(file != null, "File incremental selection is required");
        require(
                config.getSource().getPath().length() <= 2048
                        && !config.getSource().getPath().contains("\\")
                        && config.getSource().getPath().indexOf('\0') < 0,
                "File scan root must use forward slashes and be at most 2048 characters");
        require(
                incremental.getCheckpointKey() != null
                        && incremental.getCheckpointKey().matches("[A-Za-z0-9._-]{1,128}"),
                "Invalid checkpointKey");
        require(
                incremental.getWatermarkType() != null
                        && Set.of("LONG", "DECIMAL", "DATE", "TIMESTAMP", "STRING")
                                .contains(incremental.getWatermarkType().toUpperCase(Locale.ROOT)),
                "Invalid watermarkType");
        require(
                file.getMarkerLocation() != null
                        && file.getMatchMode() != null
                        && file.getSelectionMode() != null
                        && file.getChangedFilePolicy() != null,
                "File selection options are required");
        require(file.getZoneId() != null, "zoneId is required");
        ZoneId.of(file.getZoneId());
        if (file.getMarkerFormat() != null) {
            require(
                    Set.of("DATE", "TIMESTAMP")
                            .contains(incremental.getWatermarkType().toUpperCase(Locale.ROOT)),
                    "markerFormat only applies to temporal markers");
            require(
                    file.getMarkerFormat().length() <= 128 && !file.getMarkerFormat().isBlank(),
                    "Invalid markerFormat");
            EtlWatermark.formatter(file.getMarkerFormat());
        }
        new FileMarkerSelector(incremental);
        String initial =
                EtlWatermark.normalize(
                        incremental.getInitialWatermark(),
                        incremental.getWatermarkType(),
                        null,
                        file.getZoneId());
        if (file.getSelectionMode() == EtlTaskConfig.SelectionMode.LOOKBACK) {
            require(
                    file.getLookback() != null && file.getLookback().length() <= 128,
                    "lookback is required");
            EtlWatermark.subtract(initial, file.getLookback(), incremental.getWatermarkType());
        }
        if (file.getSelectionMode() == EtlTaskConfig.SelectionMode.LOWER_BOUND)
            EtlWatermark.normalize(
                    file.getLowerBound(), incremental.getWatermarkType(), null, file.getZoneId());
        if (file.getUpperBound() != null)
            EtlWatermark.normalize(
                    file.getUpperBound(), incremental.getWatermarkType(), null, file.getZoneId());
        require(file.getMaxDepth() >= 0 && file.getMaxDepth() <= 32, "Invalid maxDepth");
        require(
                file.getMaxScanEntries() >= 1 && file.getMaxScanEntries() <= 100000,
                "Invalid maxScanEntries");
        require(
                file.getMaxFiles() >= 1
                        && file.getMaxFiles() <= 10000
                        && file.getMaxFiles() <= file.getMaxScanEntries(),
                "Invalid maxFiles");
        require(
                file.getMaxTotalBytes() >= 1 && file.getMaxTotalBytes() <= 107374182400L,
                "Invalid maxTotalBytes");
        require(
                file.getStableAgeSeconds() >= 0 && file.getStableAgeSeconds() <= 604800,
                "Invalid stableAgeSeconds");
        require(
                file.getReadySuffix() == null
                        || file.getReadySuffix().matches("\\.[A-Za-z0-9._-]{1,63}"),
                "Invalid readySuffix");
        require(
                incremental.getWatermarkField() == null
                        && incremental.getUpperBoundSql() == null
                        && incremental.getTieBreakerFields() != null
                        && incremental.getTieBreakerFields().isEmpty()
                        && incremental.getInitialTieBreakerValues() != null
                        && incremental.getInitialTieBreakerValues().isEmpty(),
                "Database marker options cannot be used for file increments");
        require(
                !incremental.isAllowCheckpointPastInvalidRecords(),
                "File increments require allowCheckpointPastInvalidRecords=false");
        require(
                config.getTarget().getType() == EtlTaskConfig.TargetType.DATABASE,
                "File increments currently require a database target");
        require(
                config.getTarget().getMode() != EtlTaskConfig.LoadMode.APPEND,
                "File increments require an idempotent target mode");
        require(
                config.getTarget().getBeforeSql().isEmpty()
                        && config.getTarget().getAfterSql().isEmpty(),
                "File increments do not support target before/after SQL");
        require(
                config.getTransforms().stream()
                        .noneMatch(t -> t.getType() == TransformType.DEDUPLICATE),
                "File increments do not support cross-file DEDUPLICATE");
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }
}
