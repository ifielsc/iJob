package com.ifielsc.ijob.plugin.etl.config;

import com.ifielsc.ijob.plugin.etl.transformer.TransformConfig;
import com.ifielsc.ijob.plugin.etl.transformer.TransformType;
import com.ifielsc.ijob.plugin.etl.validator.ValidatorConfig;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class EtlTaskConfigValidator {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$#]{0,127}");
    private static final Pattern QUALIFIED_IDENTIFIER =
            Pattern.compile(
                    "[A-Za-z_][A-Za-z0-9_$#]{0,127}" + "(\\.[A-Za-z_][A-Za-z0-9_$#]{0,127}){0,2}");

    public void validateUnresolved(EtlTaskConfig config) {
        require(config != null, "ETL config is required");
        require(
                config.getVersion() == (config.messageSource() ? 2 : 1),
                "Unsupported ETL config version");
        require(config.getExecution() != null, "ETL execution config is required");
        require(
                config.getSource() != null && config.getSource().getType() != null,
                "ETL source is required");
        require(
                config.getTarget() != null && config.getTarget().getType() != null,
                "ETL target is required");
        if (config.messageSource()) validate(config);
    }

    public void validate(EtlTaskConfig config) {
        validate(config, 0);
    }

    public void validate(EtlTaskConfig config, int retryTimes) {
        require(config != null, "ETL config is required");
        require(
                config.getVersion() == (config.messageSource() ? 2 : 1),
                "Unsupported ETL config version");
        validateExecution(config.getExecution());
        validateSource(config.getSource());
        require(
                config.getSource().getSheets().size() <= config.getExecution().getMaxExcelSheets(),
                "Selected Excel sheet count exceeds maxExcelSheets");
        validateTransforms(config.getTransforms());
        validateValidations(config.getValidations());
        validateTarget(config.getTarget());
        if (config.messageSource()) validateMessage(config);
        else validateIncremental(config);
        require(
                config.getTarget().getType() != EtlTaskConfig.TargetType.DATABASE
                        || config.getTarget().getMode() != EtlTaskConfig.LoadMode.APPEND
                        || retryTimes <= 0
                        || config.isAllowNonIdempotentRetry(),
                "APPEND with task retries requires " + "allowNonIdempotentRetry=true");
    }

    private void validateMessage(EtlTaskConfig config) {
        var message = config.getMessage();
        require(message != null, "Message limits are required");
        require(
                config.getIncremental() == null || !config.getIncremental().isEnabled(),
                "Message sources use broker progress");
        require(
                config.getTarget().getType() == EtlTaskConfig.TargetType.DATABASE,
                "Message sources require database target");
        require(
                Set.of(EtlTaskConfig.LoadMode.UPSERT, EtlTaskConfig.LoadMode.INSERT_ONLY)
                        .contains(config.getTarget().getMode()),
                "Message target requires UPSERT or INSERT_ONLY");
        require(
                config.getTarget().getTransactionScope() == EtlTaskConfig.TransactionScope.BATCH,
                "Message target requires batch transactions");
        require(
                config.getTarget().getBeforeSql().isEmpty()
                        && config.getTarget().getAfterSql().isEmpty(),
                "Message target does not support SQL hooks");
        require(
                config.getTransforms().stream()
                        .noneMatch(t -> t.getType() == TransformType.DEDUPLICATE),
                "Message sources do not support DEDUPLICATE");
        require(
                config.getValidations().stream()
                        .allMatch(
                                v ->
                                        v.getOnFailure()
                                                == com.ifielsc.ijob.plugin.etl.validator
                                                        .ValidationFailurePolicy.FAIL_TASK),
                "Message validation requires FAIL_TASK");
        require(
                message.getMaxMessages() >= 1 && message.getMaxMessages() <= 10000000,
                "Invalid maxMessages");
        range(message.getIntakeSeconds(), 1, 3600, "intakeSeconds");
        range(message.getIdleSeconds(), 1, 300, "idleSeconds");
        range(message.getBatchSize(), 1, 1000, "message batchSize");
        range(message.getFlushIntervalMillis(), 100, 10000, "flushIntervalMillis");
        range(message.getMaxMessageBytes(), 1024, 16777216, "maxMessageBytes");
        range(
                message.getMaxExpandedBytes(),
                message.getMaxMessageBytes(),
                67108864,
                "maxExpandedBytes");
        range(message.getMaxJsonDepth(), 1, 100, "maxJsonDepth");
        require(
                message.getMaxTotalBytes() >= message.getMaxMessageBytes()
                        && message.getMaxTotalBytes() <= 107374182400L,
                "Invalid maxTotalBytes");
        require(
                message.getMaxBufferBytes() >= message.getMaxMessageBytes()
                        && message.getMaxBufferBytes() <= 268435456L,
                "Invalid maxBufferBytes");
        require(
                message.getMetadataColumns() != null && message.getMetadataColumns().size() <= 16,
                "Invalid metadataColumns");
        Set<String> metadata =
                config.getSource().getType() == EtlTaskConfig.SourceType.KAFKA
                        ? Set.of("topic", "partition", "offset", "key", "timestamp", "headers")
                        : Set.of(
                                "queue",
                                "exchange",
                                "routingKey",
                                "messageId",
                                "timestamp",
                                "headers",
                                "redelivered");
        requireUnique(
                new java.util.ArrayList<>(message.getMetadataColumns().values()),
                "Metadata field names");
        message.getMetadataColumns()
                .forEach(
                        (key, field) -> {
                            require(metadata.contains(key), "Unsupported metadata key");
                            requireIdentifier(field, "Metadata field name");
                        });
    }

    private void validateExecution(EtlTaskConfig.ExecutionConfig execution) {
        require(execution != null, "ETL execution config is required");
        range(execution.getReadBatchSize(), 1, 100_000, "readBatchSize");
        range(execution.getWriteBatchSize(), 1, 100_000, "writeBatchSize");
        range(execution.getQueryTimeoutSeconds(), 1, 86_400, "queryTimeoutSeconds");
        require(execution.getMaxRecords() > 0, "maxRecords must be greater than 0");
        require(execution.getMaxFileBytes() > 0, "maxFileBytes must be greater than 0");
        range(execution.getMaxRecordFields(), 1, 10_000, "maxRecordFields");
        range(execution.getMaxTextFieldChars(), 1, 16_777_216, "maxTextFieldChars");
        require(
                execution.getMaxExpandedTextChars() > 0,
                "maxExpandedTextChars must be greater than 0");
        require(execution.getMaxExcelCells() > 0, "maxExcelCells must be greater than 0");
        require(execution.getMaxStagingCells() > 0, "maxStagingCells must be greater than 0");
        range(execution.getMaxExcelSheets(), 1, 10_000, "maxExcelSheets");
    }

    private void validateSource(EtlTaskConfig.SourceConfig source) {
        require(source != null && source.getType() != null, "ETL source is required");
        requirePositive(source.getResourceId(), "source resourceId");
        require(source.getBeforeSql() != null, "source beforeSql must not be null");
        require(source.getParameters() != null, "source parameters must not be null");
        if (source.getType() == EtlTaskConfig.SourceType.KAFKA
                || source.getType() == EtlTaskConfig.SourceType.RABBITMQ) {
            require(source.getBeforeSql().isEmpty(), "Message source does not support beforeSql");
            if (source.getType() == EtlTaskConfig.SourceType.KAFKA) {
                requireText(source.getTopic(), "Kafka topic");
                require(
                        source.getTopic().matches("[a-zA-Z0-9._-]{1,249}")
                                && !Set.of(".", "..").contains(source.getTopic()),
                        "Invalid Kafka topic");
                requireText(source.getGroupId(), "Kafka groupId");
                require(
                        source.getGroupId().matches("[a-zA-Z0-9._-]{1,200}"),
                        "Invalid Kafka groupId");
                require(
                        Set.of("EARLIEST", "LATEST").contains(source.getInitialPosition()),
                        "Invalid initialPosition");
            } else {
                requireText(source.getQueue(), "RabbitMQ queue");
                require(
                        source.getQueue().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                                <= 255,
                        "RabbitMQ queue is too long");
                range(source.getPrefetch(), 1, 1000, "prefetch");
            }
            return;
        }
        validateSheets(source);
        range(source.getFetchSize(), 1, 100_000, "fetchSize");
        require(source.getSkipLines() >= 0, "skipLines must not be negative");
        if (source.getType() == EtlTaskConfig.SourceType.DATABASE) {
            requireText(source.getSql(), "source sql");
            return;
        }
        requireText(source.getPath(), "source path");
        require(source.getFormat() != null, "source format is required");
        require(source.getReadMode() != null, "source readMode is required");
        requireCharset(source.getCharset());
        if (source.getFormat() == EtlTaskConfig.FileFormat.CSV
                || source.getFormat() == EtlTaskConfig.FileFormat.TXT) {
            requireText(source.getDelimiter(), "source delimiter");
            require(
                    source.getDelimiter().length() == 1,
                    "source delimiter must contain exactly one character");
            require(
                    source.getQuote() == null
                            || source.getQuote() != source.getDelimiter().charAt(0),
                    "source quote and delimiter must be different");
        }
        require(
                source.getHeaderRow() == null || source.getHeaderRow() >= 0,
                "headerRow must not be negative");
        require(
                source.getStartRow() == null || source.getStartRow() >= 0,
                "startRow must not be negative");
        require(
                source.getSheetIndex() == null || source.getSheetIndex() >= 0,
                "sheetIndex must not be negative");
        if (source.getFormat() == EtlTaskConfig.FileFormat.REGEX) {
            requireText(source.getPattern(), "source regex pattern");
            require(source.getRegexFields() != null, "source regexFields must not be null");
            require(!source.getRegexFields().isEmpty(), "source regexFields must not be empty");
            Pattern.compile(source.getPattern());
            require(
                    "SINGLE_LINE".equalsIgnoreCase(source.getRegexMode())
                            || "BLOCK".equalsIgnoreCase(source.getRegexMode()),
                    "regexMode must be SINGLE_LINE or BLOCK");
            Set<String> fields = new HashSet<>();
            for (EtlTaskConfig.RegexFieldConfig field : source.getRegexFields()) {
                require(field != null, "regex field must not be null");
                requireText(field.getField(), "regex field name");
                require(fields.add(field.getField()), "regex field names must be unique");
                requireText(field.getGroup(), "regex group");
            }
            if ("BLOCK".equalsIgnoreCase(source.getRegexMode())) {
                requireText(source.getBlockStart(), "blockStart");
                requireText(source.getBlockEnd(), "blockEnd");
                Pattern.compile(source.getBlockStart());
                Pattern.compile(source.getBlockEnd());
            }
            range(source.getMaxBlockLines(), 1, 1_000_000, "maxBlockLines");
            range(source.getMaxBlockChars(), 1, 67_108_864, "maxBlockChars");
        }
        if (source.getReadMode() == EtlTaskConfig.FileReadMode.SQL) {
            require(
                    source.getFormat() != EtlTaskConfig.FileFormat.REGEX,
                    "regex source does not support SQL mode");
            requireText(source.getSql(), "source sql");
            requireIdentifier(source.getStagingTable(), "stagingTable");
            require(source.getStagingColumns() != null, "stagingColumns must not be null");
            requireUnique(source.getStagingColumns(), "stagingColumns");
            source.getStagingColumns()
                    .forEach(column -> requireIdentifier(column, "staging column"));
        } else {
            require(
                    source.getFormat() != EtlTaskConfig.FileFormat.SQLITE
                            && source.getFormat() != EtlTaskConfig.FileFormat.MDB,
                    "database file format requires SQL mode");
        }
    }

    private void validateSheets(EtlTaskConfig.SourceConfig source) {
        require(source.getSheets() != null, "source sheets must not be null");
        if (source.getSheets().isEmpty()) {
            return;
        }
        require(
                source.getType() == EtlTaskConfig.SourceType.FILE
                        && source.getFormat() == EtlTaskConfig.FileFormat.EXCEL
                        && source.getReadMode() == EtlTaskConfig.FileReadMode.SQL,
                "source sheets requires an Excel file source in SQL mode");
        require(
                source.getSheetName() == null && source.getSheetIndex() == null,
                "source sheets cannot be combined with source sheetName or sheetIndex");
        require(
                source.isHeader()
                        && (source.getHeaderRow() == null || source.getHeaderRow() == 0)
                        && source.getStartRow() == null,
                "With source sheets, configure header and row options on each sheet");
        require(
                "SOURCE_DATA".equals(source.getStagingTable())
                        && source.getStagingColumns() != null
                        && source.getStagingColumns().isEmpty(),
                "With source sheets, configure tableName and stagingColumns on each sheet");
        Set<String> tables = new HashSet<>();
        Set<String> selectors = new HashSet<>();
        for (EtlTaskConfig.ExcelSheetConfig sheet : source.getSheets()) {
            require(sheet != null, "Excel sheet config must not be null");
            require(
                    (sheet.getSheetName() != null) != (sheet.getSheetIndex() != null),
                    "Each Excel sheet requires exactly one of sheetName or sheetIndex");
            if (sheet.getSheetName() != null) {
                requireText(sheet.getSheetName(), "sheetName");
                require(
                        selectors.add("name:" + sheet.getSheetName().toLowerCase(Locale.ROOT)),
                        "Excel sheet selectors must be unique");
            } else {
                require(sheet.getSheetIndex() >= 0, "sheetIndex must not be negative");
                require(
                        selectors.add("index:" + sheet.getSheetIndex()),
                        "Excel sheet selectors must be unique");
            }
            requireIdentifier(sheet.getTableName(), "sheet tableName");
            require(
                    tables.add(sheet.getTableName().toLowerCase(Locale.ROOT)),
                    "Excel table names must be unique, ignoring case");
            require(
                    sheet.getHeaderRow() != null && sheet.getHeaderRow() >= 0,
                    "sheet headerRow must not be null or negative");
            require(
                    sheet.getStartRow() == null || sheet.getStartRow() >= 0,
                    "sheet startRow must not be negative");
            require(
                    !sheet.isHeader()
                            || sheet.getStartRow() == null
                            || sheet.getStartRow() > sheet.getHeaderRow(),
                    "sheet startRow must follow headerRow when header is enabled");
            require(sheet.getStagingColumns() != null, "sheet stagingColumns must not be null");
            requireUnique(sheet.getStagingColumns(), "sheet stagingColumns");
            for (String column : sheet.getStagingColumns()) {
                requireText(column, "sheet staging column");
                require(column.length() <= 128, "sheet staging column is too long");
            }
        }
    }

    private void validateTransforms(List<TransformConfig> transforms) {
        require(transforms != null, "transforms must not be null");
        for (TransformConfig transform : transforms) {
            require(transform != null && transform.getType() != null, "transform type is required");
            require(transform.getProperties() != null, "transform properties must not be null");
            Map<String, Object> properties = transform.getProperties();
            switch (transform.getType()) {
                case FIELD_MAPPING, DEFAULT_VALUE ->
                        requireNamedProperties(properties, transform.getType().name());
                case TRIM -> requireStringList(properties, "fields");
                case REPLACE -> {
                    requirePropertyText(properties, "field");
                    require(
                            properties.get("replace") instanceof Map<?, ?>,
                            "REPLACE replace must be an object");
                }
                case DATE_FORMAT -> {
                    requirePropertyText(properties, "field");
                    String source = propertyText(properties, "source");
                    String target = propertyText(properties, "target");
                    DateTimeFormatter.ofPattern(source);
                    DateTimeFormatter.ofPattern(target);
                }
                case TYPE_CONVERT -> {
                    requirePropertyText(properties, "field");
                    require(
                            Set.of(
                                            "STRING",
                                            "INTEGER",
                                            "LONG",
                                            "DOUBLE",
                                            "DECIMAL",
                                            "BOOLEAN",
                                            "DATE",
                                            "TIMESTAMP")
                                    .contains(propertyText(properties, "target")),
                            "TYPE_CONVERT target is unsupported");
                }
                case NUMBER_SCALE -> validateNumberScale(properties);
                case EXPRESSION -> {
                    requirePropertyText(properties, "target");
                    requirePropertyText(properties, "expression");
                }
                case LOOKUP -> validateLookup(properties);
                case DEDUPLICATE -> {
                    requireStringList(properties, "fields");
                    propertyInt(properties, "maxKeys", 1_000_000, 1, 10_000_000);
                    Object keep = properties.getOrDefault("keep", "FIRST");
                    require(
                            "FIRST".equalsIgnoreCase(keep.toString()),
                            "streaming DEDUPLICATE currently supports " + "keep=FIRST only");
                }
            }
        }
    }

    private void validateValidations(List<ValidatorConfig> validations) {
        require(validations != null, "validations must not be null");
        Set<String> ruleIds = new HashSet<>();
        for (ValidatorConfig validation : validations) {
            require(
                    validation != null && validation.getType() != null,
                    "validation type is required");
            requireText(validation.getRuleId(), "validation ruleId");
            require(ruleIds.add(validation.getRuleId()), "validation ruleId must be unique");
            require(validation.getOnFailure() != null, "validation onFailure is required");
            require(validation.getProperties() != null, "validation properties must not be null");
            Map<String, Object> properties = validation.getProperties();
            switch (validation.getType()) {
                case REQUIRED -> requireStringList(properties, "fields");
                case REGEX -> {
                    requirePropertyText(properties, "field");
                    Pattern.compile(propertyText(properties, "pattern"));
                }
                case RANGE -> {
                    requirePropertyText(properties, "field");
                    require(
                            properties.get("min") != null || properties.get("max") != null,
                            "RANGE requires min or max");
                    decimalProperty(properties, "min", null);
                    decimalProperty(properties, "max", null);
                }
                case ENUM -> {
                    requirePropertyText(properties, "field");
                    requireStringList(properties, "values");
                }
                case CONDITION -> requirePropertyText(properties, "expression");
                case SQL -> validateSqlRule(properties);
                case FOREIGN_KEY -> validateExistRule(properties, "FOREIGN_KEY");
            }
        }
    }

    private void validateNumberScale(Map<String, Object> properties) {
        requirePropertyText(properties, "field");
        BigDecimal divide = decimalProperty(properties, "divide", BigDecimal.ONE);
        require(divide.compareTo(BigDecimal.ZERO) != 0, "NUMBER_SCALE divide must not be zero");
        int scale = propertyInt(properties, "scale", -1, -1, 1000);
        require(scale >= -1, "NUMBER_SCALE scale is invalid");
        Object rounding = properties.get("roundingMode");
        if (rounding != null) {
            RoundingMode.valueOf(rounding.toString());
        }
        decimalProperty(properties, "multiply", BigDecimal.ONE);
        decimalProperty(properties, "add", BigDecimal.ZERO);
    }

    private void validateLookup(Map<String, Object> properties) {
        requirePropertyText(properties, "sourceField");
        requirePropertyText(properties, "targetField");
        String sql = propertyText(properties, "sql");
        require(sql.contains(":keys"), "LOOKUP sql must contain :keys");
        requirePropertyText(properties, "keyColumn");
        requirePropertyText(properties, "valueColumn");
        propertyLong(properties, "resourceId", null, 1, Long.MAX_VALUE);
        propertyInt(properties, "queryTimeoutSeconds", 300, 1, 86_400);
        propertyInt(properties, "batchSize", 500, 1, 2000);
    }

    private void validateSqlRule(Map<String, Object> properties) {
        String mode = propertyText(properties, "mode");
        require(
                "GLOBAL".equals(mode) || "FIELD".equals(mode),
                "SQL validation mode must be GLOBAL or FIELD");
        propertyLong(properties, "resourceId", null, 1, Long.MAX_VALUE);
        String sql = propertyText(properties, "sql");
        propertyInt(properties, "queryTimeoutSeconds", 300, 1, 86_400);
        if ("FIELD".equals(mode)) {
            requirePropertyText(properties, "field");
            require(sql.contains(":keys"), "SQL field validation must contain :keys");
            propertyInt(properties, "batchSize", 500, 1, 2000);
        }
    }

    private void validateExistRule(Map<String, Object> properties, String type) {
        requirePropertyText(properties, "field");
        propertyLong(properties, "resourceId", null, 1, Long.MAX_VALUE);
        require(
                propertyText(properties, "sql").contains(":keys"),
                type + " sql must contain :keys");
        propertyInt(properties, "queryTimeoutSeconds", 300, 1, 86_400);
        propertyInt(properties, "batchSize", 500, 1, 2000);
    }

    private void requireNamedProperties(Map<String, Object> properties, String type) {
        require(!properties.isEmpty(), type + " properties must not be empty");
        properties.keySet().forEach(key -> requireText(key, type + " field"));
        if ("FIELD_MAPPING".equals(type)) {
            properties
                    .values()
                    .forEach(
                            value ->
                                    require(
                                            value != null && !value.toString().isBlank(),
                                            "FIELD_MAPPING source field is required"));
        }
    }

    private void requireStringList(Map<String, Object> properties, String key) {
        Object value = properties.get(key);
        require(
                value instanceof List<?> values && !values.isEmpty(),
                key + " must be a non-empty list");
        ((List<?>) value)
                .forEach(
                        item ->
                                require(
                                        item != null && !item.toString().isBlank(),
                                        key + " must contain non-blank values"));
    }

    private void requirePropertyText(Map<String, Object> properties, String key) {
        propertyText(properties, key);
    }

    private String propertyText(Map<String, Object> properties, String key) {
        Object value = properties.get(key);
        require(value != null && !value.toString().isBlank(), key + " is required");
        return value.toString();
    }

    private BigDecimal decimalProperty(
            Map<String, Object> properties, String key, BigDecimal fallback) {
        Object value = properties.get(key);
        return value == null ? fallback : new BigDecimal(value.toString());
    }

    private int propertyInt(
            Map<String, Object> properties, String key, int fallback, int min, int max) {
        Object value = properties.get(key);
        int result =
                value == null
                        ? fallback
                        : value instanceof Number number
                                ? number.intValue()
                                : Integer.parseInt(value.toString());
        range(result, min, max, key);
        return result;
    }

    private long propertyLong(
            Map<String, Object> properties, String key, Long fallback, long min, long max) {
        Object value = properties.get(key);
        require(value != null || fallback != null, key + " is required");
        long result = value == null ? fallback : Long.parseLong(value.toString());
        require(result >= min && result <= max, key + " must be between " + min + " and " + max);
        return result;
    }

    public void validateTarget(EtlTaskConfig.TargetConfig target) {
        require(target != null && target.getType() != null, "ETL target is required");
        requirePositive(target.getResourceId(), "target resourceId");
        require(target.getColumns() != null, "target columns must not be null");
        require(target.getMappingMode() != null, "target mappingMode is required");
        boolean automatic = target.getMappingMode() == EtlTaskConfig.MappingMode.AUTO;
        require(target.getExcludedFields() != null, "target excludedFields must not be null");
        requireUnique(target.getExcludedFields(), "excludedFields");
        target.getExcludedFields().forEach(field -> requireText(field, "excluded source field"));
        require(
                automatic || target.getExcludedFields().isEmpty(),
                "Excluded fields require AUTO mapping");
        target.getColumns()
                .values()
                .forEach(
                        field ->
                                require(
                                        !target.getExcludedFields().contains(field),
                                        "An overridden source field cannot also be excluded"));

        require(target.getMode() != null, "target mode is required");
        require(target.getBeforeSql() != null, "target beforeSql must not be null");
        require(target.getAfterSql() != null, "target afterSql must not be null");
        require(target.getTransactionScope() != null, "target transactionScope is required");
        require(target.getNullPolicy() != null, "target nullPolicy is required");
        target.getColumns()
                .values()
                .forEach(sourceField -> requireText(sourceField, "target source field"));
        if (target.getType() == EtlTaskConfig.TargetType.FILE) {
            requireText(target.getPath(), "target path");
            requireCharset(target.getCharset());
            requireText(target.getDelimiter(), "target delimiter");
            require(target.getDelimiter().length() <= 8, "target delimiter is too long");
            require(
                    automatic || !target.getColumns().isEmpty(),
                    "target columns must not be empty");
            require(target.getExistingFilePolicy() != null, "existingFilePolicy is required");
            require(target.getEmptyFilePolicy() != null, "emptyFilePolicy is required");
            return;
        }
        requireText(target.getTable(), "target table");
        requireQualifiedIdentifier(target.getTable(), "target table");
        if (target.getSchema() != null && !target.getSchema().isBlank()) {
            requireIdentifier(target.getSchema(), "target schema");
        }
        require(automatic || !target.getColumns().isEmpty(), "target columns must not be empty");
        target.getColumns().keySet().forEach(column -> requireIdentifier(column, "target column"));
        require(target.getLogicalKeys() != null, "logicalKeys must not be null");
        requireUnique(target.getLogicalKeys(), "logicalKeys");
        for (String logicalKey : target.getLogicalKeys()) {
            require(
                    automatic || target.getColumns().containsKey(logicalKey),
                    "logical key must be present in target columns: " + logicalKey);
        }
        if (target.getProtection() != null) {
            requireIdentifier(target.getProtection().getColumn(), "protection column");
            require(
                    !target.getProtection().getProtectedValues().isEmpty(),
                    "protectedValues must not be empty");
        }
        require(target.getColumnNullPolicies() != null, "columnNullPolicies must not be null");
        target.getColumnNullPolicies()
                .forEach(
                        (column, policy) -> {
                            require(
                                    automatic || target.getColumns().containsKey(column),
                                    "null policy column must be mapped: " + column);
                            require(policy != null, "null policy must not be null: " + column);
                        });
        if (target.getMode() != EtlTaskConfig.LoadMode.APPEND) {
            require(
                    !target.getLogicalKeys().isEmpty()
                            || target.getMode() == EtlTaskConfig.LoadMode.SCD2,
                    "logicalKeys are required for this load mode");
        }
        if (target.getMode() == EtlTaskConfig.LoadMode.SCD2) {
            validateScd2(target.getScd2());
            target.getScd2()
                    .getBusinessKeys()
                    .forEach(
                            key ->
                                    require(
                                            automatic || target.getColumns().containsKey(key),
                                            "SCD2 business key must be mapped: " + key));
            target.getScd2()
                    .getCompareFields()
                    .forEach(
                            field ->
                                    require(
                                            automatic || target.getColumns().containsKey(field),
                                            "SCD2 compare field must be mapped: " + field));
            Set<String> generated = new HashSet<>();
            generated.add(target.getScd2().getValidFromColumn());
            require(
                    generated.add(target.getScd2().getValidToColumn()),
                    "SCD2 generated columns must be unique");
            if (target.getScd2().getCurrentFlagColumn() != null) {
                require(
                        generated.add(target.getScd2().getCurrentFlagColumn()),
                        "SCD2 generated columns must be unique");
            }
            generated.forEach(
                    column ->
                            require(
                                    !target.getColumns().containsKey(column),
                                    "SCD2 generated column must not be mapped: " + column));
        }
    }

    private void validateScd2(EtlTaskConfig.SCD2Config scd2) {
        require(scd2 != null, "scd2 config is required");
        require(scd2.getBusinessKeys() != null, "scd2 businessKeys must not be null");
        require(!scd2.getBusinessKeys().isEmpty(), "scd2 businessKeys must not be empty");
        require(scd2.getCompareFields() != null, "scd2 compareFields must not be null");
        requireUnique(scd2.getBusinessKeys(), "scd2 businessKeys");
        scd2.getBusinessKeys().forEach(key -> requireIdentifier(key, "scd2 business key"));
        scd2.getCompareFields().forEach(field -> requireIdentifier(field, "scd2 compare field"));
        requireUnique(scd2.getCompareFields(), "scd2 compareFields");
        requireText(scd2.getEffectiveValueField(), "scd2 effectiveValueField");
        requireIdentifier(scd2.getValidFromColumn(), "scd2 validFromColumn");
        requireIdentifier(scd2.getValidToColumn(), "scd2 validToColumn");
        if (scd2.getCurrentFlagColumn() != null) {
            requireIdentifier(scd2.getCurrentFlagColumn(), "scd2 currentFlagColumn");
        }
        require(scd2.getGranularity() != null, "scd2 granularity is required");
    }

    private void validateIncremental(EtlTaskConfig config) {
        EtlTaskConfig.IncrementalConfig incremental = config.getIncremental();
        if (incremental == null || !incremental.isEnabled()) {
            return;
        }
        if (config.getSource().getType() == EtlTaskConfig.SourceType.FILE) {
            FileIncrementalConfigValidator.validate(config);
            return;
        }
        require(incremental.getFile() == null, "File marker configuration requires FILE source");
        requireText(incremental.getCheckpointKey(), "checkpointKey");
        require(incremental.getCheckpointKey().length() <= 128, "checkpointKey is too long");
        requireText(incremental.getWatermarkField(), "watermarkField");
        requireText(incremental.getWatermarkType(), "watermarkType");
        require(
                Set.of("STRING", "LONG", "DECIMAL", "DATE", "TIMESTAMP")
                        .contains(incremental.getWatermarkType().toUpperCase()),
                "watermarkType is unsupported");
        require(incremental.getTieBreakerFields() != null, "tieBreakerFields must not be null");
        requireUnique(incremental.getTieBreakerFields(), "tieBreakerFields");
        require(
                incremental.getInitialTieBreakerValues() != null,
                "initialTieBreakerValues must not be null");
        require(
                incremental.getInitialTieBreakerValues().size()
                        == incremental.getTieBreakerFields().size(),
                "initialTieBreakerValues must match tieBreakerFields");
        require(
                incremental.getInitialTieBreakerValues().stream()
                        .noneMatch(java.util.Objects::isNull),
                "initialTieBreakerValues must not contain null");
        requireText(incremental.getUpperBoundSql(), "upperBoundSql");
        require(incremental.getInitialWatermark() != null, "initialWatermark is required");
        require(
                config.getSource().getSql().contains(":etlLastWatermark")
                        && config.getSource().getSql().contains(":etlUpperWatermark"),
                "incremental sql must bind :etlLastWatermark and " + ":etlUpperWatermark");
        require(
                config.getTransforms().stream()
                        .noneMatch(transform -> transform.getType() == TransformType.DEDUPLICATE),
                "incremental extraction does not support DEDUPLICATE");
        for (int index = 0; index < incremental.getTieBreakerFields().size(); index++) {
            require(
                    config.getSource().getSql().contains(":etlLastTie" + (index + 1)),
                    "incremental sql must bind :etlLastTie" + (index + 1));
        }
        if (config.getTarget().getType() == EtlTaskConfig.TargetType.DATABASE) {
            require(
                    config.getTarget().getMode() != EtlTaskConfig.LoadMode.APPEND,
                    "incremental APPEND mode is not supported");
        } else {
            require(
                    config.getTarget().getExistingFilePolicy()
                            == EtlTaskConfig.ExistingFilePolicy.OVERWRITE,
                    "incremental file output requires OVERWRITE policy");
        }
    }

    private void requireUnique(List<String> values, String field) {
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            requireText(value, field);
            require(seen.add(value), field + " contains duplicates");
        }
    }

    private void requireCharset(String charset) {
        requireText(charset, "charset");
        require(Charset.isSupported(charset), "Unsupported charset");
    }

    private void requirePositive(Long value, String field) {
        require(value != null && value > 0, field + " must be greater than 0");
    }

    private void requireIdentifier(String value, String field) {
        require(value != null && IDENTIFIER.matcher(value).matches(), field + " is invalid");
    }

    private void requireQualifiedIdentifier(String value, String field) {
        require(
                value != null && QUALIFIED_IDENTIFIER.matcher(value).matches(),
                field + " is invalid");
    }

    private void requireText(String value, String field) {
        require(value != null && !value.isBlank(), field + " is required");
    }

    private void range(int value, int min, int max, String field) {
        require(value >= min && value <= max, field + " must be between " + min + " and " + max);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
