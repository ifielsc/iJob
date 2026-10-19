// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.batchetl.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BatchEtlTaskConfig {
    private int version = 1;
    private Long sourceResourceId;
    private String sourceSchema;
    private Long targetResourceId;
    private String targetSchema;
    private List<String> tables = new ArrayList<>();
    private List<String> logicalKeys = new ArrayList<>();
    private ExtractMode extractMode = ExtractMode.FULL;
    private IncrementalConfig incremental = new IncrementalConfig();
    private LoadMode loadMode = LoadMode.UPSERT;
    private NullPolicy nullPolicy = NullPolicy.OVERWRITE;
    private TargetNamingConfig targetNaming = new TargetNamingConfig();
    private boolean clearBeforeImport;
    private ClearMode clearMode = ClearMode.DELETE;
    private boolean acknowledgeNonTransactionalTruncate;
    private boolean acknowledgePartialCommit;
    private boolean allowNonIdempotentRetry;
    private ExecutionConfig execution = new ExecutionConfig();

    @Data
    public static class IncrementalConfig {
        private String watermarkColumn;
        private String watermarkType = "TIMESTAMP";
        private Object initialWatermark;
        private List<Object> initialLogicalKeyValues = new ArrayList<>();
    }

    @Data
    public static class TargetNamingConfig {
        private String prefix = "";
        private String suffix = "";
    }

    @Data
    public static class ExecutionConfig {
        private int fetchSize = 2000;
        private int writeBatchSize = 1000;
        private int queryTimeoutSeconds = 300;
        private int maxTables = 500;
        private long maxRowsPerTable = 10_000_000L;
        private long maxTotalRows = 50_000_000L;
    }

    public enum ExtractMode {
        FULL,
        INCREMENTAL
    }

    public enum LoadMode {
        UPSERT,
        INSERT_ONLY,
        UPDATE_ONLY,
        APPEND
    }

    public enum NullPolicy {
        OVERWRITE,
        KEEP_TARGET
    }

    public enum ClearMode {
        DELETE,
        TRUNCATE
    }
}
