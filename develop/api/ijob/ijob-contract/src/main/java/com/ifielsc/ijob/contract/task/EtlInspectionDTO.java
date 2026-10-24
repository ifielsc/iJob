// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.task;

import java.time.LocalDateTime;
import java.util.List;

public final class EtlInspectionDTO {
    private EtlInspectionDTO() {}

    public record Progress(String revision, String mode, List<Checkpoint> checkpoints) {}

    public record Checkpoint(
            String sourceTable,
            String targetTable,
            String checkpointKey,
            String watermarkField,
            String watermarkType,
            String initialValue,
            String watermarkValue,
            String tieBreakerValue,
            String version,
            String lastInstanceId,
            LocalDateTime updateTime,
            String status,
            String configurationMatch) {}

    public record Tables(String schema, List<Table> tables, boolean truncated) {}

    public record Table(String name, String type) {}

    public record Check(String sourceTable, String targetTable, String status, String message) {}

    public record Checks(List<Check> tables) {}

    public record CheckRequest(String pluginConfig) {}
}
