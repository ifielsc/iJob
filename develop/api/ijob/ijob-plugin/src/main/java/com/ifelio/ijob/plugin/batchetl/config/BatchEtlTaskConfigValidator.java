package com.ifelio.ijob.plugin.batchetl.config;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class BatchEtlTaskConfigValidator {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$#]{0,127}");
    private static final Pattern NAME_FRAGMENT = Pattern.compile("[A-Za-z0-9_$#]{0,64}");
    private static final Set<String> WATERMARK_TYPES =
            Set.of("LONG", "DECIMAL", "DATE", "TIMESTAMP", "STRING");

    public void validateUnresolved(BatchEtlTaskConfig config) {
        require(config != null, "Batch ETL config is required");
        require(config.getVersion() == 1, "Unsupported Batch ETL config version");
        require(
                config.getTables() != null && !config.getTables().isEmpty(),
                "Batch ETL tables must not be empty");
        require(config.getExecution() != null, "Batch ETL execution config is required");
    }

    public void validate(BatchEtlTaskConfig config, int retryTimes) {
        validateUnresolved(config);
        requirePositive(config.getSourceResourceId(), "sourceResourceId");
        requirePositive(config.getTargetResourceId(), "targetResourceId");
        requireIdentifierIfPresent(config.getSourceSchema(), "sourceSchema");
        requireIdentifierIfPresent(config.getTargetSchema(), "targetSchema");
        validateTables(config);
        validateKeys(config.getLogicalKeys());
        validateNaming(config.getTargetNaming());
        validateExecution(config.getExecution(), config.getTables().size());
        require(config.getExtractMode() != null, "extractMode is required");
        require(config.getLoadMode() != null, "loadMode is required");
        require(config.getNullPolicy() != null, "nullPolicy is required");
        require(config.getClearMode() != null, "clearMode is required");
        if (config.getLoadMode() != BatchEtlTaskConfig.LoadMode.APPEND) {
            require(
                    !config.getLogicalKeys().isEmpty(),
                    "logicalKeys are required for keyed load modes");
        }
        validateIncremental(config);
        validateClear(config);
        require(
                config.getLoadMode() != BatchEtlTaskConfig.LoadMode.APPEND
                        || retryTimes <= 0
                        || config.isAllowNonIdempotentRetry(),
                "APPEND with task retries requires " + "allowNonIdempotentRetry=true");
    }

    private void validateTables(BatchEtlTaskConfig config) {
        require(
                config.getTables().size() <= config.getExecution().getMaxTables(),
                "tables exceed maxTables");
        Set<String> unique = new HashSet<>();
        for (String table : config.getTables()) {
            requireIdentifier(table, "table");
            require(unique.add(table.toUpperCase(Locale.ROOT)), "tables must be unique");
        }
    }

    private void validateKeys(List<String> keys) {
        require(keys != null, "logicalKeys must not be null");
        Set<String> unique = new HashSet<>();
        for (String key : keys) {
            requireIdentifier(key, "logical key");
            require(unique.add(key.toUpperCase(Locale.ROOT)), "logicalKeys must be unique");
        }
    }

    private void validateNaming(BatchEtlTaskConfig.TargetNamingConfig naming) {
        require(naming != null, "targetNaming is required");
        require(
                naming.getPrefix() != null && NAME_FRAGMENT.matcher(naming.getPrefix()).matches(),
                "target table prefix is invalid");
        require(
                naming.getSuffix() != null && NAME_FRAGMENT.matcher(naming.getSuffix()).matches(),
                "target table suffix is invalid");
    }

    private void validateExecution(BatchEtlTaskConfig.ExecutionConfig execution, int tableCount) {
        require(execution != null, "execution is required");
        range(execution.getFetchSize(), 1, 100_000, "fetchSize");
        range(execution.getWriteBatchSize(), 1, 100_000, "writeBatchSize");
        range(execution.getQueryTimeoutSeconds(), 1, 86_400, "queryTimeoutSeconds");
        range(execution.getMaxTables(), 1, 500, "maxTables");
        require(tableCount <= execution.getMaxTables(), "tables exceed maxTables");
        require(execution.getMaxRowsPerTable() > 0, "maxRowsPerTable must be greater than 0");
        require(execution.getMaxTotalRows() > 0, "maxTotalRows must be greater than 0");
        require(
                execution.getMaxTotalRows() >= execution.getMaxRowsPerTable(),
                "maxTotalRows must be at least maxRowsPerTable");
    }

    private void validateIncremental(BatchEtlTaskConfig config) {
        BatchEtlTaskConfig.IncrementalConfig incremental = config.getIncremental();
        require(incremental != null, "incremental config is required");
        require(
                incremental.getInitialLogicalKeyValues() != null,
                "initialLogicalKeyValues must not be null");
        if (config.getExtractMode() != BatchEtlTaskConfig.ExtractMode.INCREMENTAL) {
            return;
        }
        require(!config.getLogicalKeys().isEmpty(), "incremental mode requires logicalKeys");
        require(
                config.getLoadMode() != BatchEtlTaskConfig.LoadMode.APPEND,
                "incremental mode does not support APPEND");
        requireIdentifier(incremental.getWatermarkColumn(), "watermarkColumn");
        require(incremental.getInitialWatermark() != null, "initialWatermark is required");
        require(
                incremental.getWatermarkType() != null
                        && WATERMARK_TYPES.contains(
                                incremental.getWatermarkType().toUpperCase(Locale.ROOT)),
                "watermarkType is unsupported");
        require(
                incremental.getInitialLogicalKeyValues().size() == config.getLogicalKeys().size(),
                "initialLogicalKeyValues must match logicalKeys");
        require(
                incremental.getInitialLogicalKeyValues().stream()
                        .noneMatch(java.util.Objects::isNull),
                "initialLogicalKeyValues must not contain null");
    }

    private void validateClear(BatchEtlTaskConfig config) {
        if (!config.isClearBeforeImport()) {
            require(
                    config.getClearMode() != BatchEtlTaskConfig.ClearMode.TRUNCATE
                            || !config.isAcknowledgeNonTransactionalTruncate(),
                    "TRUNCATE acknowledgement requires clearBeforeImport");
            return;
        }
        require(
                config.getExtractMode() == BatchEtlTaskConfig.ExtractMode.FULL,
                "clearBeforeImport is only allowed in FULL mode");
        if (config.getClearMode() == BatchEtlTaskConfig.ClearMode.TRUNCATE) {
            require(
                    config.isAcknowledgeNonTransactionalTruncate(),
                    "TRUNCATE requires explicit non-transactional " + "risk acknowledgement");
        } else {
            require(
                    config.isAcknowledgePartialCommit(),
                    "DELETE clear with batch commits requires " + "acknowledgePartialCommit=true");
        }
    }

    private void requireIdentifierIfPresent(String value, String name) {
        if (value != null && !value.isBlank()) {
            requireIdentifier(value, name);
        }
    }

    private void requireIdentifier(String value, String name) {
        require(value != null && IDENTIFIER.matcher(value).matches(), name + " is invalid");
    }

    private void requirePositive(Long value, String name) {
        require(value != null && value > 0, name + " must be greater than 0");
    }

    private void range(int value, int min, int max, String name) {
        require(value >= min && value <= max, name + " must be between " + min + " and " + max);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
