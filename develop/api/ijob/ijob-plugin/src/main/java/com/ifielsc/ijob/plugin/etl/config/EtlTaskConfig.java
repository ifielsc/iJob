// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.config;

import com.ifielsc.ijob.plugin.etl.transformer.TransformConfig;
import com.ifielsc.ijob.plugin.etl.validator.ValidatorConfig;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class EtlTaskConfig {
    private int version = 1;
    private ExecutionConfig execution = new ExecutionConfig();
    private SourceConfig source;
    private List<TransformConfig> transforms = new ArrayList<>();
    private List<ValidatorConfig> validations = new ArrayList<>();
    private TargetConfig target;
    private IncrementalConfig incremental = new IncrementalConfig();
    private boolean allowNonIdempotentRetry;
    private MessageConfig message = new MessageConfig();

    public boolean messageSource() {
        return source != null
                && (source.type == SourceType.KAFKA || source.type == SourceType.RABBITMQ);
    }

    @Data
    public static class MessageConfig {
        private long maxMessages = 10000;
        private int intakeSeconds = 60;
        private int idleSeconds = 5;
        private int batchSize = 100;
        private int flushIntervalMillis = 1000;
        private int maxMessageBytes = 1048576;
        private int maxExpandedBytes = 8388608;
        private long maxTotalBytes = 1073741824L;
        private long maxBufferBytes = 33554432L;
        private int maxJsonDepth = 32;
        private Map<String, String> metadataColumns = new LinkedHashMap<>();
    }

    @Data
    public static class ExecutionConfig {
        private int readBatchSize = 2000;
        private int writeBatchSize = 1000;
        private int queryTimeoutSeconds = 300;
        private long maxRecords = 10_000_000L;
        private long maxFileBytes = 1_073_741_824L;
        private int maxRecordFields = 1000;
        private int maxTextFieldChars = 1_048_576;
        private long maxExpandedTextChars = 268_435_456L;
        private long maxExcelCells = 50_000_000L;
        private long maxStagingCells = 50_000_000L;
        private int maxExcelSheets = 100;
    }

    @Data
    public static class SourceConfig {
        private SourceType type;
        private String topic;
        private String groupId;
        private String queue;
        private String initialPosition = "EARLIEST";
        private int prefetch = 100;
        private Long resourceId;
        private List<String> beforeSql = new ArrayList<>();
        private String sql;
        private Map<String, Object> parameters = new LinkedHashMap<>();
        private int fetchSize = 2000;
        private String path;
        private FileFormat format;
        private FileReadMode readMode = FileReadMode.DIRECT;
        private String charset = "UTF-8";
        private String delimiter = ",";
        private Character quote = '"';
        private Character escape = '"';
        private boolean header = true;
        private int skipLines;
        private Integer headerRow = 0;
        private Integer startRow;
        private String sheetName;
        private Integer sheetIndex;
        private List<ExcelSheetConfig> sheets = new ArrayList<>();
        private String regexMode = "SINGLE_LINE";
        private String pattern;
        private String blockStart;
        private String blockEnd;
        private int maxBlockLines = 10_000;
        private int maxBlockChars = 4_194_304;
        private List<RegexFieldConfig> regexFields = new ArrayList<>();
        private String stagingTable = "SOURCE_DATA";
        private List<String> stagingColumns = new ArrayList<>();
    }

    @Data
    public static class ExcelSheetConfig {
        private String sheetName;
        private Integer sheetIndex;
        private String tableName;
        private boolean header = true;
        private Integer headerRow = 0;
        private Integer startRow;
        private List<String> stagingColumns = new ArrayList<>();
    }

    @Data
    public static class RegexFieldConfig {
        private String field;
        private String group;
        private String defaultValue;
    }

    @Data
    public static class TargetConfig {
        private TargetType type;
        private Long resourceId;
        private String schema;
        private String table;
        private MappingMode mappingMode = MappingMode.MANUAL;
        private List<String> excludedFields = new ArrayList<>();
        private Map<String, String> columns = new LinkedHashMap<>();
        private List<String> logicalKeys = new ArrayList<>();
        private LoadMode mode = LoadMode.APPEND;
        private List<String> beforeSql = new ArrayList<>();
        private List<String> afterSql = new ArrayList<>();
        private TransactionScope transactionScope = TransactionScope.BATCH;
        private NullPolicy nullPolicy = NullPolicy.KEEP_TARGET;
        private Map<String, NullPolicy> columnNullPolicies = new LinkedHashMap<>();
        private ProtectionConfig protection;
        private SCD2Config scd2;
        private String path;
        private String charset = "UTF-8";
        private String delimiter = ",";
        private Character quote = '"';
        private boolean header = true;
        private ExistingFilePolicy existingFilePolicy = ExistingFilePolicy.FAIL;
        private EmptyFilePolicy emptyFilePolicy = EmptyFilePolicy.CREATE_WITH_HEADER;
    }

    @Data
    public static class ProtectionConfig {
        private String column;
        private List<Object> protectedValues = new ArrayList<>();
        private ProtectedRecordPolicy onProtected = ProtectedRecordPolicy.SKIP;
    }

    @Data
    public static class SCD2Config {
        private List<String> businessKeys = new ArrayList<>();
        private List<String> compareFields = new ArrayList<>();
        private String effectiveValueField;
        private String validFromColumn;
        private String validToColumn;
        private String currentFlagColumn;
        private Object currentValue = true;
        private Object historyValue = false;
        private SCD2Granularity granularity = SCD2Granularity.TIMESTAMP;
    }

    @Data
    public static class IncrementalConfig {
        private boolean enabled;
        private String checkpointKey;
        private String watermarkField;
        private String watermarkType = "STRING";
        private List<String> tieBreakerFields = new ArrayList<>();
        private List<Object> initialTieBreakerValues = new ArrayList<>();
        private String upperBoundSql;
        private Object initialWatermark;
        private boolean allowCheckpointPastInvalidRecords = true;
        private FileIncrementalConfig file;
    }

    @Data
    public static class FileIncrementalConfig {
        private MarkerLocation markerLocation = MarkerLocation.NAME;
        private MatchMode matchMode = MatchMode.TEMPLATE;
        private String expression;
        private String markerFormat;
        private String zoneId = "UTC";
        private SelectionMode selectionMode = SelectionMode.STRICT;
        private String lookback;
        private Object lowerBound;
        private Object upperBound;
        private ChangedFilePolicy changedFilePolicy = ChangedFilePolicy.FAIL;
        private boolean recursive;
        private int maxDepth = 8;
        private int maxScanEntries = 10000;
        private int maxFiles = 1000;
        private long maxTotalBytes = 1073741824L;
        private int stableAgeSeconds = 60;
        private String readySuffix;
    }

    public enum MappingMode {
        MANUAL,
        AUTO
    }

    public enum MarkerLocation {
        NAME,
        RELATIVE_PATH
    }

    public enum MatchMode {
        TEMPLATE,
        REGEX
    }

    public enum SelectionMode {
        STRICT,
        LOOKBACK,
        LOWER_BOUND
    }

    public enum ChangedFilePolicy {
        FAIL,
        REPROCESS
    }

    public enum SourceType {
        DATABASE,
        FILE,
        KAFKA,
        RABBITMQ
    }

    public enum FileFormat {
        CSV,
        TXT,
        EXCEL,
        REGEX,
        SQLITE,
        MDB
    }

    public enum FileReadMode {
        DIRECT,
        SQL
    }

    public enum TargetType {
        DATABASE,
        FILE
    }

    public enum LoadMode {
        UPSERT,
        INSERT_ONLY,
        UPDATE_ONLY,
        APPEND,
        SCD2
    }

    public enum TransactionScope {
        RUN,
        BATCH
    }

    public enum NullPolicy {
        KEEP_TARGET,
        OVERWRITE
    }

    public enum ProtectedRecordPolicy {
        SKIP,
        FAIL
    }

    public enum ExistingFilePolicy {
        FAIL,
        OVERWRITE,
        SKIP
    }

    public enum EmptyFilePolicy {
        CREATE_WITH_HEADER,
        CREATE_EMPTY,
        SKIP
    }

    public enum SCD2Granularity {
        DAY,
        TIMESTAMP
    }
}
