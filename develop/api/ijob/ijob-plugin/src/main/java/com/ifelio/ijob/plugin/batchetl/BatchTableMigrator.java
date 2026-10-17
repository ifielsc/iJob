package com.ifelio.ijob.plugin.batchetl;

import com.ifelio.ijob.core.etl.EtlCheckpointSnapshot;
import com.ifelio.ijob.core.etl.EtlCheckpointStore;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.plugin.batchetl.config.BatchEtlTaskConfig;
import com.ifelio.ijob.plugin.batchetl.metadata.BatchTablePlan;
import com.ifelio.ijob.plugin.common.jdbc.CancelableJdbcResources;
import com.ifelio.ijob.plugin.common.jdbc.JdbcColumnValues;
import com.ifelio.ijob.plugin.common.jdbc.JdbcIdentifierQuoter;
import com.ifelio.ijob.plugin.common.jdbc.JdbcStreaming;
import com.ifelio.ijob.plugin.common.jdbc.JdbcValueKey;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class BatchTableMigrator {
    private final EtlCheckpointStore checkpointStore;

    public BatchEtlResult.TableResult migrate(
            Connection sourceConnection,
            Connection targetConnection,
            CancelableJdbcResources sourceResources,
            CancelableJdbcResources targetResources,
            BatchTablePlan plan,
            BatchEtlTaskConfig config,
            TaskContext context,
            long remainingRows)
            throws SQLException {
        JdbcIdentifierQuoter sourceQuoter =
                JdbcIdentifierQuoter.from(sourceConnection.getMetaData());
        JdbcIdentifierQuoter targetQuoter =
                JdbcIdentifierQuoter.from(targetConnection.getMetaData());
        IncrementalState incremental =
                openIncremental(
                        sourceConnection, sourceResources, sourceQuoter, plan, config, context);
        String sql = selectSql(sourceQuoter, plan, config, incremental.enabled());
        try (PreparedStatement statement =
                sourceConnection.prepareStatement(
                        sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            sourceResources.attach(statement);
            JdbcStreaming.configureFetch(
                    statement,
                    sourceConnection.getMetaData(),
                    config.getExecution().getFetchSize());
            statement.setQueryTimeout(config.getExecution().getQueryTimeoutSeconds());
            bindIncremental(statement, incremental, plan);
            try (ResultSet rows = statement.executeQuery()) {
                sourceResources.attach(rows);
                return copyRows(
                        rows,
                        targetConnection,
                        targetResources,
                        targetQuoter,
                        plan,
                        config,
                        context,
                        incremental,
                        remainingRows);
            }
        }
    }

    private BatchEtlResult.TableResult copyRows(
            ResultSet rows,
            Connection targetConnection,
            CancelableJdbcResources targetResources,
            JdbcIdentifierQuoter targetQuoter,
            BatchTablePlan plan,
            BatchEtlTaskConfig config,
            TaskContext context,
            IncrementalState incremental,
            long remainingRows)
            throws SQLException {
        BatchTableLoader loader =
                new BatchTableLoader(targetConnection, targetResources, targetQuoter, plan, config);
        List<Object[]> batch = new ArrayList<>(config.getExecution().getWriteBatchSize());
        Counts total = new Counts();
        Set<JdbcValueKey> seenKeys = new HashSet<>();
        while (rows.next()) {
            context.checkStop();
            Object[] row = readRow(rows, plan);
            ensureUniqueKey(row, seenKeys, plan);
            batch.add(row);
            total.extracted++;
            if (total.extracted > config.getExecution().getMaxRowsPerTable()) {
                throw new IllegalArgumentException(
                        "Batch ETL table exceeds maxRowsPerTable: " + plan.sourceTable());
            }
            if (total.extracted > remainingRows) {
                throw new IllegalArgumentException("Batch ETL run exceeds maxTotalRows");
            }
            if (batch.size() >= config.getExecution().getWriteBatchSize()) {
                writeBatch(loader, batch, total, incremental, targetConnection, context, plan);
            }
        }
        if (!batch.isEmpty()) {
            writeBatch(loader, batch, total, incremental, targetConnection, context, plan);
        }
        if (total.extracted == 0) {
            context.checkStop();
            commit(targetConnection, context);
            recordCommittedBatch(context, 0, new BatchTableLoader.LoadCounts(0, 0, 0), plan);
        }
        return new BatchEtlResult.TableResult(
                plan.sourceTable(),
                plan.targetTable(),
                total.extracted,
                total.inserted,
                total.updated,
                total.skipped);
    }

    private void writeBatch(
            BatchTableLoader loader,
            List<Object[]> batch,
            Counts total,
            IncrementalState incremental,
            Connection targetConnection,
            TaskContext context,
            BatchTablePlan plan)
            throws SQLException {
        context.checkStop();
        context.checkPause();
        BatchTableLoader.LoadCounts counts = loader.write(batch);
        context.checkStop();
        commit(targetConnection, context);
        recordCommittedBatch(context, batch.size(), counts, plan);
        total.inserted += counts.inserted();
        total.updated += counts.updated();
        total.skipped += counts.skipped();
        if (incremental.enabled()) {
            Object[] last = batch.getLast();
            Object watermark = value(last, plan, plan.watermark());
            if (watermark == null) {
                throw new IllegalArgumentException(
                        "Batch ETL watermark must not be null: " + plan.watermark().sourceColumn());
            }
            List<Object> keys = keyValues(last, plan);
            incremental.advance(
                    context.getInstanceId(),
                    BatchCheckpointValues.stored(watermark),
                    keys.stream().map(BatchCheckpointValues::stored).toList());
        }
        batch.clear();
    }

    private void recordCommittedBatch(
            TaskContext context,
            int extracted,
            BatchTableLoader.LoadCounts counts,
            BatchTablePlan plan) {
        increment(context, "batchEtl.committedBatches", 1);
        increment(context, "batchEtl.committedRows", extracted);
        increment(context, "batchEtl.committedInserts", counts.inserted());
        increment(context, "batchEtl.committedUpdates", counts.updated());
        increment(context, "batchEtl.committedSkips", counts.skipped());
        context.putVariable("batchEtl.lastCommittedTable", plan.targetTable());
    }

    static void commit(Connection connection, TaskContext context) throws SQLException {
        context.checkStop();
        context.putVariable("batchEtl.commitOutcomeUnknown", true);
        connection.commit();
        context.putVariable("batchEtl.commitOutcomeUnknown", false);
    }

    private void increment(TaskContext context, String name, long amount) {
        Object current = context.getVariable(name);
        long value = current instanceof Number number ? number.longValue() : 0L;
        context.putVariable(name, value + amount);
    }

    void ensureUniqueKey(Object[] row, Set<JdbcValueKey> seen, BatchTablePlan plan) {
        if (plan.logicalKeys().isEmpty()) {
            return;
        }
        JdbcValueKey current = JdbcValueKey.ofValues(keyValues(row, plan));
        if (!seen.add(current)) {
            throw new IllegalArgumentException(
                    "Batch ETL source logical key is not unique: " + plan.sourceTable());
        }
    }

    private Object[] readRow(ResultSet rows, BatchTablePlan plan) throws SQLException {
        Object[] result = new Object[plan.columns().size()];
        for (int index = 0; index < result.length; index++) {
            result[index] =
                    JdbcColumnValues.read(
                            rows, index + 1, plan.columns().get(index).sourceJdbcType());
        }
        return result;
    }

    private IncrementalState openIncremental(
            Connection sourceConnection,
            CancelableJdbcResources resources,
            JdbcIdentifierQuoter quoter,
            BatchTablePlan plan,
            BatchEtlTaskConfig config,
            TaskContext context)
            throws SQLException {
        if (config.getExtractMode() != BatchEtlTaskConfig.ExtractMode.INCREMENTAL) {
            return IncrementalState.disabled();
        }
        EtlCheckpointSnapshot snapshot =
                checkpointStore.loadOrCreate(
                        context.getInstanceId(),
                        checkpointKey(config, plan),
                        fingerprint(config, plan),
                        config.getIncremental().getWatermarkType().toUpperCase(Locale.ROOT),
                        config.getIncremental().getInitialWatermark(),
                        config.getIncremental().getInitialLogicalKeyValues());
        String sql =
                "SELECT MAX("
                        + quoter.quote(plan.watermark().sourceColumn())
                        + ") FROM "
                        + quoter.qualify(plan.sourceSchema(), plan.sourceTable());
        Object upper;
        try (PreparedStatement statement = sourceConnection.prepareStatement(sql)) {
            resources.attach(statement);
            statement.setQueryTimeout(config.getExecution().getQueryTimeoutSeconds());
            try (ResultSet rows = statement.executeQuery()) {
                resources.attach(rows);
                if (!rows.next()) {
                    throw new IllegalStateException("Batch ETL upper bound query returned no row");
                }
                upper = rows.getObject(1);
            }
        }
        return new IncrementalState(checkpointStore, snapshot, upper);
    }

    String selectSql(
            JdbcIdentifierQuoter quoter,
            BatchTablePlan plan,
            BatchEtlTaskConfig config,
            boolean incremental) {
        StringBuilder sql = new StringBuilder("SELECT ");
        quoter.append(
                sql,
                plan.columns().stream().map(BatchTablePlan.ColumnMapping::sourceColumn).toList());
        sql.append(" FROM ").append(quoter.qualify(plan.sourceSchema(), plan.sourceTable()));
        if (incremental) {
            String watermark = quoter.quote(plan.watermark().sourceColumn());
            sql.append(" WHERE (")
                    .append(watermark)
                    .append(" > ? OR (")
                    .append(watermark)
                    .append(" = ? AND (");
            appendTiePredicate(sql, quoter, plan.logicalKeys(), 0);
            sql.append("))) AND ").append(watermark).append(" <= ?");
        }
        List<BatchTablePlan.ColumnMapping> order =
                incremental ? orderedIncrementalColumns(plan) : plan.logicalKeys();
        if (!order.isEmpty()) {
            sql.append(" ORDER BY ");
            quoter.append(
                    sql, order.stream().map(BatchTablePlan.ColumnMapping::sourceColumn).toList());
        }
        return sql.toString();
    }

    private void appendTiePredicate(
            StringBuilder sql,
            JdbcIdentifierQuoter quoter,
            List<BatchTablePlan.ColumnMapping> keys,
            int index) {
        String key = quoter.quote(keys.get(index).sourceColumn());
        sql.append(key).append(" > ?");
        if (index + 1 < keys.size()) {
            sql.append(" OR (").append(key).append(" = ? AND ");
            appendTiePredicate(sql, quoter, keys, index + 1);
            sql.append(')');
        }
    }

    private void bindIncremental(
            PreparedStatement statement, IncrementalState incremental, BatchTablePlan plan)
            throws SQLException {
        if (!incremental.enabled()) {
            return;
        }
        int parameter = 1;
        Object watermark =
                BatchCheckpointValues.jdbc(
                        incremental.snapshot().watermark(), plan.watermark().sourceJdbcType());
        statement.setObject(parameter++, watermark);
        statement.setObject(parameter++, watermark);
        List<Object> ties = incremental.snapshot().tieBreakers();
        if (ties.size() != plan.logicalKeys().size())
            throw new IllegalArgumentException(
                    "Batch ETL checkpoint logical keys do not match the plan");
        for (int index = 0; index < ties.size(); index++) {
            Object tie =
                    BatchCheckpointValues.jdbc(
                            ties.get(index), plan.logicalKeys().get(index).sourceJdbcType());
            statement.setObject(parameter++, tie);
            if (index + 1 < plan.logicalKeys().size()) {
                statement.setObject(parameter++, tie);
            }
        }
        statement.setObject(parameter, incremental.upperBound());
    }

    private List<BatchTablePlan.ColumnMapping> orderedIncrementalColumns(BatchTablePlan plan) {
        List<BatchTablePlan.ColumnMapping> result = new ArrayList<>();
        result.add(plan.watermark());
        result.addAll(plan.logicalKeys());
        return result;
    }

    private List<Object> keyValues(Object[] row, BatchTablePlan plan) {
        List<Object> result = new ArrayList<>(plan.logicalKeys().size());
        for (BatchTablePlan.ColumnMapping key : plan.logicalKeys()) {
            Object value = value(row, plan, key);
            if (value == null) {
                throw new IllegalArgumentException(
                        "Batch ETL logical key must not be null: " + key.sourceColumn());
            }
            result.add(value);
        }
        return List.copyOf(result);
    }

    private Object value(Object[] row, BatchTablePlan plan, BatchTablePlan.ColumnMapping column) {
        for (int index = 0; index < plan.columns().size(); index++) {
            if (plan.columns().get(index).equals(column)) {
                return row[index];
            }
        }
        throw new IllegalStateException("Batch ETL column is not selected");
    }

    private String checkpointKey(BatchEtlTaskConfig config, BatchTablePlan plan) {
        return "batch:"
                + hash(
                        (plan.sourceSchema() == null ? "" : plan.sourceSchema())
                                + "."
                                + plan.sourceTable());
    }

    private String fingerprint(BatchEtlTaskConfig config, BatchTablePlan plan) {
        return hash(
                config.getSourceResourceId()
                        + "\n"
                        + config.getTargetResourceId()
                        + "\n"
                        + plan.sourceSchema()
                        + "\n"
                        + plan.targetSchema()
                        + "\n"
                        + plan.sourceTable()
                        + "\n"
                        + plan.targetTable()
                        + "\n"
                        + plan.columns()
                        + "\n"
                        + plan.logicalKeys()
                        + "\n"
                        + plan.watermark()
                        + "\n"
                        + config.getIncremental().getWatermarkType());
    }

    private String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static final class Counts {
        private long extracted;
        private long inserted;
        private long updated;
        private long skipped;
    }

    private static final class IncrementalState {
        private final EtlCheckpointStore store;
        private EtlCheckpointSnapshot snapshot;
        private final Object upperBound;

        private IncrementalState(
                EtlCheckpointStore store, EtlCheckpointSnapshot snapshot, Object upperBound) {
            this.store = store;
            this.snapshot = snapshot;
            this.upperBound = upperBound;
        }

        private static IncrementalState disabled() {
            return new IncrementalState(null, null, null);
        }

        private boolean enabled() {
            return store != null;
        }

        private EtlCheckpointSnapshot snapshot() {
            return snapshot;
        }

        private Object upperBound() {
            return upperBound;
        }

        private void advance(Long instanceId, Object watermark, List<Object> keys) {
            snapshot = store.advanceAndGet(snapshot, instanceId, watermark, keys);
        }
    }
}
