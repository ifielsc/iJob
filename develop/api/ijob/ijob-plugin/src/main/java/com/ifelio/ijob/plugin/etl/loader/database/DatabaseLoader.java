package com.ifelio.ijob.plugin.etl.loader.database;

import com.ifelio.ijob.core.resource.ResourceResolver;
import com.ifelio.ijob.plugin.common.jdbc.JdbcIdentifierQuoter;
import com.ifelio.ijob.plugin.common.jdbc.JdbcValueKey;
import com.ifelio.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifelio.ijob.plugin.etl.loader.LoadBatchResult;
import com.ifelio.ijob.plugin.etl.loader.Loader;
import com.ifelio.ijob.plugin.etl.loader.TargetMappingPlan;
import com.ifelio.ijob.plugin.etl.model.DataRecord;
import com.ifelio.ijob.plugin.etl.model.EtlContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

public final class DatabaseLoader implements Loader {
    private static final int EXISTENCE_QUERY_BATCH = 100;

    private final ResourceResolver resourceResolver;
    private EtlTaskConfig.TargetConfig config;
    private final boolean automatic;
    private boolean mappingResolved;
    private boolean messageKeysVerified;
    private final AtomicBoolean closed = new AtomicBoolean();
    private EtlContext context;
    private Connection connection;
    private volatile Statement activeStatement;
    private String table;
    private JdbcIdentifierQuoter identifierQuoter;
    private boolean completed;
    private long pendingRunRows;

    public DatabaseLoader(ResourceResolver resourceResolver, EtlTaskConfig.TargetConfig config) {
        this.resourceResolver = resourceResolver;
        this.config = config;
        this.automatic = config.getMappingMode() == EtlTaskConfig.MappingMode.AUTO;
    }

    @Override
    public void open(EtlContext context) {
        this.context = context;
        try {
            DataSource dataSource = resourceResolver.getDataSource(config.getResourceId());
            attachConnection(dataSource.getConnection());
            connection.setAutoCommit(false);
            identifierQuoter = JdbcIdentifierQuoter.from(connection.getMetaData());
            table = qualifiedTable();
            executeScripts(config.getBeforeSql());
        } catch (Exception exception) {
            close();
            throw new IllegalStateException("ETL database target could not be opened", exception);
        }
    }

    @Override
    public LoadBatchResult writeBatch(List<DataRecord> records) {
        try {
            if (automatic && !records.isEmpty()) {
                if (!mappingResolved) {
                    try (PreparedStatement statement =
                                    prepare("SELECT * FROM " + table + " WHERE 1=0");
                            ResultSet result = statement.executeQuery()) {
                        config =
                                TargetMappingPlan.resolve(
                                        context,
                                        config,
                                        TargetColumnMetadata.read(connection, result.getMetaData()),
                                        records);
                        mappingResolved = true;
                    }
                } else TargetMappingPlan.validateRows(context, records);
            }
            if (context.getConfig().messageSource() && !records.isEmpty() && !messageKeysVerified) {
                try (var statement = prepare("SELECT * FROM " + table + " WHERE 1=0");
                        var result = statement.executeQuery()) {
                    MessageTargetKeys.verify(
                            connection, result.getMetaData(), config.getLogicalKeys());
                    messageKeysVerified = true;
                }
            }
            TargetMappingPlan.validateMappedFields(config, records);
            LoadBatchResult total = new LoadBatchResult(0, 0, 0, 0, 0);
            int batchSize =
                    context.getConfig().messageSource()
                            ? context.getConfig().getMessage().getBatchSize()
                            : context.getConfig().getExecution().getWriteBatchSize();
            for (int from = 0; from < records.size(); from += batchSize) {
                context.checkControl();
                int to = Math.min(records.size(), from + batchSize);
                List<DataRecord> batch = records.subList(from, to);
                LoadBatchResult result =
                        switch (config.getMode()) {
                            case APPEND -> insert(batch);
                            case INSERT_ONLY, UPDATE_ONLY, UPSERT -> {
                                if (!context.getConfig().messageSource())
                                    yield mergeByLogicalKey(batch);
                                LoadBatchResult ordered = new LoadBatchResult(0, 0, 0, 0, 0);
                                for (DataRecord row : batch)
                                    ordered = add(ordered, mergeByLogicalKey(List.of(row)));
                                yield ordered;
                            }
                            case SCD2 -> loadScd2(batch);
                        };
                total = add(total, result);
                if (config.getTransactionScope() == EtlTaskConfig.TransactionScope.BATCH) {
                    context.getWriteGuard().run();
                    connection.commit();
                    recordCommittedBatch(result.loaded());
                } else {
                    pendingRunRows += result.loaded();
                }
            }
            return total;
        } catch (Exception exception) {
            rollback(exception);
            throw new IllegalStateException("ETL database target write failed", exception);
        }
    }

    private LoadBatchResult add(LoadBatchResult left, LoadBatchResult right) {
        return new LoadBatchResult(
                left.loaded() + right.loaded(),
                left.inserted() + right.inserted(),
                left.updated() + right.updated(),
                left.skipped() + right.skipped(),
                left.protectedRecords() + right.protectedRecords());
    }

    private void recordCommittedBatch(long loadedRows) {
        Object current = context.getTaskContext().getVariable("etl.committedBatches");
        long batches = current instanceof Number number ? number.longValue() : 0L;
        context.getTaskContext().putVariable("etl.committedBatches", batches + 1);
        Object rowsValue = context.getTaskContext().getVariable("etl.committedRows");
        long rows = rowsValue instanceof Number number ? number.longValue() : 0L;
        context.getTaskContext().putVariable("etl.committedRows", rows + loadedRows);
    }

    private LoadBatchResult mergeByLogicalKey(List<DataRecord> records) throws SQLException {
        Set<JdbcValueKey> existing = findExistingKeys(records);
        List<DataRecord> inserts = new ArrayList<>();
        List<DataRecord> updates = new ArrayList<>();
        long skipped = 0;
        for (DataRecord record : records) {
            boolean exists = existing.contains(keyOf(record));
            switch (config.getMode()) {
                case UPSERT -> (exists ? updates : inserts).add(record);
                case INSERT_ONLY -> {
                    if (exists) {
                        skipped++;
                    } else {
                        inserts.add(record);
                    }
                }
                case UPDATE_ONLY -> {
                    if (exists) {
                        updates.add(record);
                    } else {
                        skipped++;
                    }
                }
                default -> throw new IllegalStateException("Unexpected ETL load mode");
            }
        }
        LoadBatchResult insertResult = insert(inserts);
        LoadBatchResult updateResult = update(updates);
        return new LoadBatchResult(
                insertResult.loaded() + updateResult.loaded(),
                insertResult.inserted(),
                updateResult.updated(),
                skipped + updateResult.skipped(),
                updateResult.protectedRecords());
    }

    private LoadBatchResult loadScd2(List<DataRecord> records) throws SQLException {
        long inserted = 0;
        long updated = 0;
        long skipped = 0;
        long protectedRecords = 0;
        for (DataRecord record : records) {
            EtlTaskConfig.SCD2Config scd2 = config.getScd2();
            Object effective = effectiveValue(record, scd2);
            CurrentScdRow current = selectCurrentScd(record, scd2);
            int effectiveOrder =
                    current == null ? 1 : compareEffective(effective, current.validFrom(), scd2);
            if (effectiveOrder < 0) {
                throw new IllegalArgumentException(
                        "ETL SCD2 effective value precedes current version");
            }
            if (current != null && current.protectedRecord()) {
                if (config.getProtection().getOnProtected()
                        == EtlTaskConfig.ProtectedRecordPolicy.FAIL) {
                    throw new IllegalStateException("ETL target record is protected");
                }
                skipped++;
                protectedRecords++;
                continue;
            }
            if (current != null && current.matches()) {
                skipped++;
                continue;
            }
            if (current != null) {
                if (effectiveOrder == 0) {
                    throw new IllegalArgumentException(
                            "ETL SCD2 changed values require a later " + "effective value");
                }
                closeCurrentScd(record, scd2, effective);
                updated++;
            }
            insertScd(record, scd2, effective);
            inserted++;
        }
        return new LoadBatchResult(inserted, inserted, updated, skipped, protectedRecords);
    }

    private CurrentScdRow selectCurrentScd(DataRecord record, EtlTaskConfig.SCD2Config scd2)
            throws SQLException {
        List<String> selected = new ArrayList<>(scd2.getCompareFields());
        if (!selected.contains(scd2.getValidFromColumn())) {
            selected.add(scd2.getValidFromColumn());
        }
        EtlTaskConfig.ProtectionConfig protection = config.getProtection();
        if (protection != null && !selected.contains(protection.getColumn())) {
            selected.add(protection.getColumn());
        }
        StringBuilder sql = new StringBuilder("SELECT ");
        if (selected.isEmpty()) {
            sql.append('1');
        } else {
            appendIdentifiers(sql, selected);
        }
        sql.append(" FROM ").append(table).append(" WHERE ");
        appendScdKeys(sql, scd2.getBusinessKeys());
        sql.append(" AND ").append(identifier(scd2.getValidToColumn())).append(" IS NULL");
        if (scd2.getCurrentFlagColumn() != null) {
            sql.append(" AND ").append(identifier(scd2.getCurrentFlagColumn())).append(" = ?");
        }
        try (PreparedStatement statement = prepare(sql.toString())) {
            statement.setQueryTimeout(queryTimeout());
            int parameter = bindScdKeys(statement, 1, record, scd2.getBusinessKeys());
            if (scd2.getCurrentFlagColumn() != null) {
                statement.setObject(parameter, scd2.getCurrentValue());
            }
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                boolean matches = true;
                for (String field : scd2.getCompareFields()) {
                    if (!valuesEqual(rows.getObject(field), sourceValue(record, field))) {
                        matches = false;
                    }
                }
                Object protectionValue =
                        protection == null ? null : rows.getObject(protection.getColumn());
                boolean protectedRecord =
                        protection != null
                                && protection.getProtectedValues().stream()
                                        .anyMatch(
                                                value ->
                                                        JdbcValueKey.valuesEqual(
                                                                value, protectionValue));
                Object validFrom = rows.getObject(scd2.getValidFromColumn());
                if (rows.next()) {
                    throw new IllegalStateException("ETL SCD2 target has multiple current rows");
                }
                return new CurrentScdRow(matches, protectedRecord, validFrom);
            }
        }
    }

    private void closeCurrentScd(DataRecord record, EtlTaskConfig.SCD2Config scd2, Object effective)
            throws SQLException {
        StringBuilder sql =
                new StringBuilder("UPDATE ")
                        .append(table)
                        .append(" SET ")
                        .append(identifier(scd2.getValidToColumn()))
                        .append(" = ?");
        if (scd2.getCurrentFlagColumn() != null) {
            sql.append(',').append(identifier(scd2.getCurrentFlagColumn())).append(" = ?");
        }
        sql.append(" WHERE ");
        appendScdKeys(sql, scd2.getBusinessKeys());
        sql.append(" AND ").append(identifier(scd2.getValidToColumn())).append(" IS NULL");
        try (PreparedStatement statement = prepare(sql.toString())) {
            statement.setQueryTimeout(queryTimeout());
            int parameter = 1;
            statement.setObject(parameter++, effective);
            if (scd2.getCurrentFlagColumn() != null) {
                statement.setObject(parameter++, scd2.getHistoryValue());
            }
            bindScdKeys(statement, parameter, record, scd2.getBusinessKeys());
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("ETL SCD2 current row could not be closed");
            }
        }
    }

    private void insertScd(DataRecord record, EtlTaskConfig.SCD2Config scd2, Object effective)
            throws SQLException {
        List<String> columns = new ArrayList<>(config.getColumns().keySet());
        columns.add(scd2.getValidFromColumn());
        columns.add(scd2.getValidToColumn());
        if (scd2.getCurrentFlagColumn() != null) {
            columns.add(scd2.getCurrentFlagColumn());
        }
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(table).append(" (");
        appendIdentifiers(sql, columns);
        sql.append(") VALUES (")
                .append(String.join(",", java.util.Collections.nCopies(columns.size(), "?")))
                .append(')');
        try (PreparedStatement statement = prepare(sql.toString())) {
            statement.setQueryTimeout(queryTimeout());
            int parameter = 1;
            for (String column : config.getColumns().keySet()) {
                statement.setObject(parameter++, sourceValue(record, column));
            }
            statement.setObject(parameter++, effective);
            statement.setObject(parameter++, null);
            if (scd2.getCurrentFlagColumn() != null) {
                statement.setObject(parameter, scd2.getCurrentValue());
            }
            statement.executeUpdate();
        }
    }

    private Object effectiveValue(DataRecord record, EtlTaskConfig.SCD2Config scd2) {
        Object value = record.get(scd2.getEffectiveValueField());
        if (value == null) {
            throw new IllegalArgumentException("ETL SCD2 effective value must not be null");
        }
        return normalizeEffective(value, scd2);
    }

    private Object normalizeEffective(Object value, EtlTaskConfig.SCD2Config scd2) {
        return switch (scd2.getGranularity()) {
            case DAY ->
                    value instanceof java.sql.Date date
                            ? date.toLocalDate()
                            : value instanceof java.time.LocalDate date
                                    ? date
                                    : java.time.LocalDate.parse(value.toString());
            case TIMESTAMP ->
                    value instanceof java.sql.Timestamp timestamp
                            ? timestamp.toLocalDateTime()
                            : value instanceof java.time.LocalDateTime dateTime
                                    ? dateTime
                                    : java.time.LocalDateTime.parse(value.toString());
        };
    }

    private int compareEffective(Object left, Object right, EtlTaskConfig.SCD2Config scd2) {
        if (right == null) {
            throw new IllegalStateException("ETL SCD2 current valid-from value is null");
        }
        Object normalizedLeft = normalizeEffective(left, scd2);
        Object normalizedRight = normalizeEffective(right, scd2);
        return switch (scd2.getGranularity()) {
            case DAY ->
                    ((java.time.LocalDate) normalizedLeft)
                            .compareTo((java.time.LocalDate) normalizedRight);
            case TIMESTAMP ->
                    ((java.time.LocalDateTime) normalizedLeft)
                            .compareTo((java.time.LocalDateTime) normalizedRight);
        };
    }

    private boolean valuesEqual(Object left, Object right) {
        return JdbcValueKey.valuesEqual(left, right);
    }

    private void appendScdKeys(StringBuilder sql, List<String> keys) {
        for (int index = 0; index < keys.size(); index++) {
            if (index > 0) {
                sql.append(" AND ");
            }
            sql.append(identifier(keys.get(index))).append(" = ?");
        }
    }

    private int bindScdKeys(
            PreparedStatement statement, int parameter, DataRecord record, List<String> keys)
            throws SQLException {
        for (String key : keys) {
            Object value = sourceValue(record, key);
            if (value == null) {
                throw new IllegalArgumentException(
                        "ETL SCD2 business key must not be null: " + key);
            }
            statement.setObject(parameter++, value);
        }
        return parameter;
    }

    private Set<JdbcValueKey> findExistingKeys(List<DataRecord> records) throws SQLException {
        Set<JdbcValueKey> result = new HashSet<>();
        for (int from = 0; from < records.size(); from += EXISTENCE_QUERY_BATCH) {
            int to = Math.min(records.size(), from + EXISTENCE_QUERY_BATCH);
            List<DataRecord> part = records.subList(from, to);
            StringBuilder sql = new StringBuilder("SELECT ");
            appendIdentifiers(sql, config.getLogicalKeys());
            sql.append(" FROM ").append(table).append(" WHERE ");
            for (int row = 0; row < part.size(); row++) {
                if (row > 0) {
                    sql.append(" OR ");
                }
                sql.append('(');
                for (int key = 0; key < config.getLogicalKeys().size(); key++) {
                    if (key > 0) {
                        sql.append(" AND ");
                    }
                    sql.append(identifier(config.getLogicalKeys().get(key))).append(" = ?");
                }
                sql.append(')');
            }
            try (PreparedStatement statement = prepare(sql.toString())) {
                statement.setQueryTimeout(queryTimeout());
                int parameter = 1;
                for (DataRecord record : part) {
                    for (Object value : keyValues(record)) {
                        statement.setObject(parameter++, value);
                    }
                }
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        List<Object> key = new ArrayList<>(config.getLogicalKeys().size());
                        for (int index = 0; index < config.getLogicalKeys().size(); index++) {
                            key.add(rows.getObject(index + 1));
                        }
                        result.add(JdbcValueKey.ofValues(key));
                    }
                }
            }
        }
        return result;
    }

    private LoadBatchResult insert(List<DataRecord> records) throws SQLException {
        if (records.isEmpty()) {
            return new LoadBatchResult(0, 0, 0, 0, 0);
        }
        List<String> columns = List.copyOf(config.getColumns().keySet());
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(table).append(" (");
        appendIdentifiers(sql, columns);
        sql.append(") VALUES (")
                .append(String.join(",", java.util.Collections.nCopies(columns.size(), "?")))
                .append(')');
        try (PreparedStatement statement = prepare(sql.toString())) {
            statement.setQueryTimeout(queryTimeout());
            for (DataRecord record : records) {
                for (int index = 0; index < columns.size(); index++) {
                    statement.setObject(index + 1, sourceValue(record, columns.get(index)));
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
        return new LoadBatchResult(records.size(), records.size(), 0, 0, 0);
    }

    private LoadBatchResult update(List<DataRecord> records) throws SQLException {
        if (records.isEmpty()) {
            return new LoadBatchResult(0, 0, 0, 0, 0);
        }
        Map<List<String>, List<DataRecord>> groups = new LinkedHashMap<>();
        for (DataRecord record : records) {
            List<String> fields = updatableColumns(record);
            if (fields.isEmpty()) {
                groups.computeIfAbsent(List.of(), ignored -> new ArrayList<>()).add(record);
            } else {
                groups.computeIfAbsent(fields, ignored -> new ArrayList<>()).add(record);
            }
        }
        long updated = 0;
        long skipped = groups.getOrDefault(List.of(), List.of()).size();
        long protectedRecords = 0;
        for (Map.Entry<List<String>, List<DataRecord>> entry : groups.entrySet()) {
            if (entry.getKey().isEmpty()) {
                continue;
            }
            UpdateCounts counts = executeUpdates(entry.getKey(), entry.getValue());
            updated += counts.updated();
            skipped += counts.skipped();
            protectedRecords += counts.protectedRecords();
        }
        return new LoadBatchResult(updated, 0, updated, skipped, protectedRecords);
    }

    private UpdateCounts executeUpdates(List<String> fields, List<DataRecord> records)
            throws SQLException {
        StringBuilder sql = new StringBuilder("UPDATE ").append(table).append(" SET ");
        for (int index = 0; index < fields.size(); index++) {
            if (index > 0) {
                sql.append(',');
            }
            sql.append(identifier(fields.get(index))).append(" = ?");
        }
        sql.append(" WHERE ");
        for (int index = 0; index < config.getLogicalKeys().size(); index++) {
            if (index > 0) {
                sql.append(" AND ");
            }
            sql.append(identifier(config.getLogicalKeys().get(index))).append(" = ?");
        }
        appendProtection(sql);
        try (PreparedStatement statement = prepare(sql.toString())) {
            statement.setQueryTimeout(queryTimeout());
            for (DataRecord record : records) {
                int parameter = 1;
                for (String field : fields) {
                    statement.setObject(parameter++, sourceValue(record, field));
                }
                for (Object value : keyValues(record)) {
                    statement.setObject(parameter++, value);
                }
                parameter = bindProtection(statement, parameter);
                statement.addBatch();
            }
            int[] counts = statement.executeBatch();
            long updated = 0;
            long skipped = 0;
            long protectedRecords = 0;
            for (int count : counts) {
                if (count > 0 || count == Statement.SUCCESS_NO_INFO) {
                    updated++;
                } else {
                    if (config.getProtection() == null) {
                        throw new IllegalStateException("ETL target row changed concurrently");
                    }
                    if (config.getProtection().getOnProtected()
                            == EtlTaskConfig.ProtectedRecordPolicy.FAIL) {
                        throw new IllegalStateException("ETL target record is protected");
                    }
                    skipped++;
                    protectedRecords++;
                }
            }
            return new UpdateCounts(updated, skipped, protectedRecords);
        }
    }

    private List<String> updatableColumns(DataRecord record) {
        List<String> result = new ArrayList<>();
        for (String column : config.getColumns().keySet()) {
            if (config.getLogicalKeys().contains(column)) {
                continue;
            }
            Object value = sourceValue(record, column);
            EtlTaskConfig.NullPolicy policy =
                    config.getColumnNullPolicies().getOrDefault(column, config.getNullPolicy());
            if (value != null || policy == EtlTaskConfig.NullPolicy.OVERWRITE) {
                result.add(column);
            }
        }
        return List.copyOf(result);
    }

    private void appendProtection(StringBuilder sql) {
        EtlTaskConfig.ProtectionConfig protection = config.getProtection();
        if (protection == null || protection.getProtectedValues().isEmpty()) {
            return;
        }
        String column = identifier(protection.getColumn());
        sql.append(" AND (")
                .append(column)
                .append(" IS NULL OR ")
                .append(column)
                .append(" NOT IN (")
                .append(
                        String.join(
                                ",",
                                java.util.Collections.nCopies(
                                        protection.getProtectedValues().size(), "?")))
                .append("))");
    }

    private int bindProtection(PreparedStatement statement, int parameter) throws SQLException {
        EtlTaskConfig.ProtectionConfig protection = config.getProtection();
        if (protection == null) {
            return parameter;
        }
        for (Object value : protection.getProtectedValues()) {
            statement.setObject(parameter++, value);
        }
        return parameter;
    }

    private List<Object> keyValues(DataRecord record) {
        List<Object> key = new ArrayList<>(config.getLogicalKeys().size());
        for (String column : config.getLogicalKeys()) {
            Object value = sourceValue(record, column);
            if (value == null) {
                throw new IllegalArgumentException("ETL logical key must not be null: " + column);
            }
            key.add(value);
        }
        return List.copyOf(key);
    }

    private JdbcValueKey keyOf(DataRecord record) {
        return JdbcValueKey.ofValues(keyValues(record));
    }

    private Object sourceValue(DataRecord record, String targetColumn) {
        String sourceField = config.getColumns().get(targetColumn);
        return record.get(sourceField);
    }

    @Override
    public void flush() {
        try {
            context.checkControl();
            executeScripts(config.getAfterSql());
            context.getWriteGuard().run();
            connection.commit();
            if (config.getTransactionScope() == EtlTaskConfig.TransactionScope.RUN) {
                recordCommittedBatch(pendingRunRows);
                pendingRunRows = 0;
            }
            completed = true;
        } catch (Exception exception) {
            rollback(exception);
            throw new IllegalStateException("ETL database target finalization failed", exception);
        }
    }

    private void executeScripts(List<String> scripts) throws SQLException {
        for (String sql : scripts) {
            context.checkControl();
            try (Statement statement = createStatement()) {
                statement.setQueryTimeout(queryTimeout());
                statement.execute(context.getTaskContext().resolve(sql));
            }
        }
    }

    private int queryTimeout() {
        return context.getConfig().getExecution().getQueryTimeoutSeconds();
    }

    private void attachConnection(Connection opened) throws SQLException {
        connection = opened;
        if (closed.get()) {
            try {
                opened.close();
            } catch (SQLException ignored) {
                // Preserve the stop outcome.
            }
            throw new SQLException("ETL database target was stopped");
        }
    }

    private PreparedStatement prepare(String sql) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        attachStatement(statement);
        return statement;
    }

    private Statement createStatement() throws SQLException {
        Statement statement = connection.createStatement();
        attachStatement(statement);
        return statement;
    }

    private void attachStatement(Statement statement) throws SQLException {
        activeStatement = statement;
        if (closed.get()) {
            try {
                statement.cancel();
            } catch (SQLException ignored) {
                // Closing remains the fallback.
            }
            try {
                statement.close();
            } catch (SQLException ignored) {
                // Preserve the stop outcome.
            }
            throw new SQLException("ETL database target was stopped");
        }
    }

    private String qualifiedTable() {
        String raw =
                config.getTable().contains(".")
                                || config.getSchema() == null
                                || config.getSchema().isBlank()
                        ? config.getTable()
                        : config.getSchema() + "." + config.getTable();
        String[] parts = raw.split("\\.");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!result.isEmpty()) {
                result.append('.');
            }
            result.append(identifier(part));
        }
        return result.toString();
    }

    private void appendIdentifiers(StringBuilder sql, List<String> identifiers) {
        for (int index = 0; index < identifiers.size(); index++) {
            if (index > 0) {
                sql.append(',');
            }
            sql.append(identifier(identifiers.get(index)));
        }
    }

    private String identifier(String value) {
        return identifierQuoter.quote(value);
    }

    private void rollback(Exception primary) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            primary.addSuppressed(rollbackFailure);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true) || connection == null) {
            return;
        }
        Statement statement = activeStatement;
        if (statement != null) {
            try {
                statement.cancel();
            } catch (SQLException ignored) {
                // Connection close remains the fallback.
            }
        }
        if (!completed) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
                // Closing the connection is the final cleanup path.
            }
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            throw new IllegalStateException("ETL database target cleanup failed", exception);
        }
    }

    private record UpdateCounts(long updated, long skipped, long protectedRecords) {}

    private record CurrentScdRow(boolean matches, boolean protectedRecord, Object validFrom) {}
}
