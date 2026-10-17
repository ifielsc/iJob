package com.ifelio.ijob.plugin.batchetl;

import com.ifelio.ijob.plugin.batchetl.config.BatchEtlTaskConfig;
import com.ifelio.ijob.plugin.batchetl.metadata.BatchTablePlan;
import com.ifelio.ijob.plugin.common.jdbc.CancelableJdbcResources;
import com.ifelio.ijob.plugin.common.jdbc.JdbcIdentifierQuoter;
import com.ifelio.ijob.plugin.common.jdbc.JdbcValueKey;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BatchTableLoader {
    private static final int EXISTENCE_QUERY_BATCH = 100;

    private final Connection connection;
    private final CancelableJdbcResources resources;
    private final JdbcIdentifierQuoter quoter;
    private final BatchTablePlan plan;
    private final BatchEtlTaskConfig config;
    private final String table;

    BatchTableLoader(
            Connection connection,
            CancelableJdbcResources resources,
            JdbcIdentifierQuoter quoter,
            BatchTablePlan plan,
            BatchEtlTaskConfig config) {
        this.connection = connection;
        this.resources = resources;
        this.quoter = quoter;
        this.plan = plan;
        this.config = config;
        table = quoter.qualify(plan.targetSchema(), plan.targetTable());
    }

    LoadCounts write(List<Object[]> rows) throws SQLException {
        if (config.getLoadMode() == BatchEtlTaskConfig.LoadMode.APPEND) {
            return new LoadCounts(insert(rows), 0, 0);
        }
        Set<JdbcValueKey> existing = findExistingKeys(rows);
        List<Object[]> inserts = new ArrayList<>();
        List<Object[]> updates = new ArrayList<>();
        long skipped = 0;
        for (Object[] row : rows) {
            boolean exists = existing.contains(keyOf(row));
            switch (config.getLoadMode()) {
                case UPSERT -> (exists ? updates : inserts).add(row);
                case INSERT_ONLY -> {
                    if (exists) {
                        skipped++;
                    } else {
                        inserts.add(row);
                    }
                }
                case UPDATE_ONLY -> {
                    if (exists) {
                        updates.add(row);
                    } else {
                        skipped++;
                    }
                }
                case APPEND -> throw new IllegalStateException("Unexpected APPEND load mode");
            }
        }
        long inserted = insert(inserts);
        UpdateCounts updateCounts = update(updates);
        return new LoadCounts(inserted, updateCounts.updated(), skipped + updateCounts.skipped());
    }

    private Set<JdbcValueKey> findExistingKeys(List<Object[]> rows) throws SQLException {
        Set<JdbcValueKey> result = new HashSet<>();
        for (int from = 0; from < rows.size(); from += EXISTENCE_QUERY_BATCH) {
            int to = Math.min(rows.size(), from + EXISTENCE_QUERY_BATCH);
            List<Object[]> part = rows.subList(from, to);
            StringBuilder sql = new StringBuilder("SELECT ");
            quoter.append(
                    sql,
                    plan.logicalKeys().stream()
                            .map(BatchTablePlan.ColumnMapping::targetColumn)
                            .toList());
            sql.append(" FROM ").append(table).append(" WHERE ");
            for (int row = 0; row < part.size(); row++) {
                if (row > 0) {
                    sql.append(" OR ");
                }
                sql.append('(');
                for (int key = 0; key < plan.logicalKeys().size(); key++) {
                    if (key > 0) {
                        sql.append(" AND ");
                    }
                    sql.append(quoter.quote(plan.logicalKeys().get(key).targetColumn()))
                            .append(" = ?");
                }
                sql.append(')');
            }
            try (PreparedStatement statement = prepare(sql.toString())) {
                int parameter = 1;
                for (Object[] row : part) {
                    for (Object value : keyValues(row)) {
                        statement.setObject(parameter++, value);
                    }
                }
                try (ResultSet found = statement.executeQuery()) {
                    resources.attach(found);
                    while (found.next()) {
                        List<Object> values = new ArrayList<>(plan.logicalKeys().size());
                        for (int key = 1; key <= plan.logicalKeys().size(); key++) {
                            values.add(found.getObject(key));
                        }
                        if (!result.add(JdbcValueKey.ofValues(values))) {
                            throw new IllegalArgumentException(
                                    "Batch ETL target logical key is not "
                                            + "unique: "
                                            + plan.targetTable());
                        }
                    }
                }
            }
        }
        return result;
    }

    private long insert(List<Object[]> rows) throws SQLException {
        if (rows.isEmpty()) {
            return 0;
        }
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(table).append(" (");
        quoter.append(
                sql,
                plan.columns().stream().map(BatchTablePlan.ColumnMapping::targetColumn).toList());
        sql.append(") VALUES (")
                .append(String.join(",", Collections.nCopies(plan.columns().size(), "?")))
                .append(')');
        try (PreparedStatement statement = prepare(sql.toString())) {
            for (Object[] row : rows) {
                bindRow(statement, row, allColumnIndexes(), 1);
                statement.addBatch();
            }
            return successful(statement.executeBatch());
        }
    }

    private UpdateCounts update(List<Object[]> rows) throws SQLException {
        Map<List<Integer>, List<Object[]>> groups = new LinkedHashMap<>();
        long skipped = 0;
        for (Object[] row : rows) {
            List<Integer> columns = updateColumnIndexes(row);
            if (columns.isEmpty()) {
                skipped++;
            } else {
                groups.computeIfAbsent(columns, ignored -> new ArrayList<>()).add(row);
            }
        }
        long updated = 0;
        for (Map.Entry<List<Integer>, List<Object[]>> group : groups.entrySet()) {
            StringBuilder sql = new StringBuilder("UPDATE ").append(table).append(" SET ");
            for (int index = 0; index < group.getKey().size(); index++) {
                if (index > 0) {
                    sql.append(',');
                }
                sql.append(
                                quoter.quote(
                                        plan.columns()
                                                .get(group.getKey().get(index))
                                                .targetColumn()))
                        .append(" = ?");
            }
            sql.append(" WHERE ");
            appendKeyPredicate(sql);
            try (PreparedStatement statement = prepare(sql.toString())) {
                for (Object[] row : group.getValue()) {
                    int parameter = bindRow(statement, row, group.getKey(), 1);
                    bindValues(statement, keyValues(row), parameter);
                    statement.addBatch();
                }
                int[] counts = statement.executeBatch();
                requireSingleRowUpdates(counts);
                updated += successful(counts);
                skipped += zero(counts);
            }
        }
        return new UpdateCounts(updated, skipped);
    }

    private List<Integer> updateColumnIndexes(Object[] row) {
        List<Integer> result = new ArrayList<>();
        for (int index = 0; index < plan.columns().size(); index++) {
            BatchTablePlan.ColumnMapping column = plan.columns().get(index);
            if (plan.logicalKeys().contains(column)) {
                continue;
            }
            if (config.getNullPolicy() == BatchEtlTaskConfig.NullPolicy.OVERWRITE
                    || row[index] != null) {
                result.add(index);
            }
        }
        return List.copyOf(result);
    }

    private List<Integer> allColumnIndexes() {
        List<Integer> result = new ArrayList<>(plan.columns().size());
        for (int index = 0; index < plan.columns().size(); index++) {
            result.add(index);
        }
        return result;
    }

    private void appendKeyPredicate(StringBuilder sql) {
        for (int index = 0; index < plan.logicalKeys().size(); index++) {
            if (index > 0) {
                sql.append(" AND ");
            }
            sql.append(quoter.quote(plan.logicalKeys().get(index).targetColumn())).append(" = ?");
        }
    }

    private int bindRow(
            PreparedStatement statement, Object[] row, List<Integer> columns, int parameter)
            throws SQLException {
        for (Integer column : columns) {
            statement.setObject(parameter++, row[column]);
        }
        return parameter;
    }

    private int bindValues(PreparedStatement statement, List<Object> values, int parameter)
            throws SQLException {
        for (Object value : values) {
            statement.setObject(parameter++, value);
        }
        return parameter;
    }

    private List<Object> keyValues(Object[] row) {
        List<Object> result = new ArrayList<>(plan.logicalKeys().size());
        for (BatchTablePlan.ColumnMapping key : plan.logicalKeys()) {
            int index = plan.columns().indexOf(key);
            Object value = row[index];
            if (value == null) {
                throw new IllegalArgumentException(
                        "Batch ETL logical key must not be null: " + key.sourceColumn());
            }
            result.add(value);
        }
        return result;
    }

    private JdbcValueKey keyOf(Object[] row) {
        return JdbcValueKey.ofValues(keyValues(row));
    }

    private PreparedStatement prepare(String sql) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        resources.attach(statement);
        statement.setQueryTimeout(config.getExecution().getQueryTimeoutSeconds());
        return statement;
    }

    private long successful(int[] counts) {
        return Arrays.stream(counts)
                .filter(value -> value > 0 || value == Statement.SUCCESS_NO_INFO)
                .count();
    }

    private long zero(int[] counts) {
        return Arrays.stream(counts).filter(value -> value == 0).count();
    }

    private void requireSingleRowUpdates(int[] counts) {
        if (Arrays.stream(counts).anyMatch(value -> value > 1)) {
            throw new IllegalArgumentException(
                    "Batch ETL target logical key is not unique: " + plan.targetTable());
        }
    }

    record LoadCounts(long inserted, long updated, long skipped) {}

    private record UpdateCounts(long updated, long skipped) {}
}
