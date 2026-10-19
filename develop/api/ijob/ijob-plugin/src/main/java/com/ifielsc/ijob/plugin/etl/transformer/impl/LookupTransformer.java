// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.transformer.impl;

import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.plugin.common.jdbc.CancelableJdbcResources;
import com.ifielsc.ijob.plugin.common.jdbc.JdbcValueKey;
import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.model.EtlContext;
import com.ifielsc.ijob.plugin.etl.transformer.AbstractTransformer;
import com.ifielsc.ijob.plugin.etl.transformer.TransformConfig;
import com.ifielsc.ijob.plugin.etl.transformer.TransformType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

@Component
@RequiredArgsConstructor
public class LookupTransformer extends AbstractTransformer {
    private final ResourceResolver resourceResolver;

    @Override
    public String type() {
        return TransformType.LOOKUP.name();
    }

    @Override
    public List<DataRecord> transform(List<DataRecord> records, TransformConfig config) {
        return transform(records, config, null);
    }

    @Override
    public List<DataRecord> transform(
            List<DataRecord> records, TransformConfig config, EtlContext context) {
        String sourceField = getString(config, "sourceField");
        String targetField = getString(config, "targetField");
        String sql = getString(config, "sql");
        String keyColumn = getString(config, "keyColumn");
        String valueColumn = getString(config, "valueColumn");
        long resourceId = number(config, "resourceId").longValue();
        int queryTimeout = optionalNumber(config, "queryTimeoutSeconds", 300).intValue();
        int lookupBatchSize = optionalNumber(config, "batchSize", 500).intValue();
        if (lookupBatchSize <= 0 || lookupBatchSize > 2000) {
            throw new IllegalArgumentException("LOOKUP batchSize must be between 1 and 2000");
        }
        Set<Object> keys = new LinkedHashSet<>();
        for (DataRecord record : records) {
            Object key = record.get(sourceField);
            if (key != null) {
                keys.add(key);
            }
        }
        Map<JdbcValueKey, Object> values =
                keys.isEmpty()
                        ? Map.of()
                        : query(
                                resourceResolver.getDataSource(resourceId),
                                sql,
                                keyColumn,
                                valueColumn,
                                keys,
                                queryTimeout,
                                lookupBatchSize,
                                context);
        Object defaultValue = config.getProperties().get("defaultValue");
        boolean keepOriginal =
                Boolean.TRUE.equals(config.getProperties().get("keepOriginalWhenMissing"));
        for (DataRecord record : records) {
            Object key = record.get(sourceField);
            JdbcValueKey lookupKey = JdbcValueKey.of(key);
            if (key == null || !values.containsKey(lookupKey)) {
                if (!keepOriginal) {
                    record.put(targetField, defaultValue);
                }
            } else {
                record.put(targetField, values.get(lookupKey));
            }
        }
        return records;
    }

    private Map<JdbcValueKey, Object> query(
            DataSource dataSource,
            String sql,
            String keyColumn,
            String valueColumn,
            Set<Object> keys,
            int timeout,
            int batchSize,
            EtlContext context) {
        if (!sql.contains(":keys")) {
            throw new IllegalArgumentException("LOOKUP sql must contain :keys");
        }
        List<Object> keyList = new ArrayList<>(keys);
        Map<JdbcValueKey, Object> result = new HashMap<>();
        CancelableJdbcResources resources = new CancelableJdbcResources();
        if (context != null) {
            context.getTaskContext().registerResource(resources);
        }
        try {
            if (context != null) {
                context.checkControl();
            }
            Connection connection = dataSource.getConnection();
            resources.attach(connection);
            for (int from = 0; from < keyList.size(); from += batchSize) {
                if (context != null) {
                    context.checkControl();
                }
                List<Object> batch =
                        keyList.subList(from, Math.min(keyList.size(), from + batchSize));
                String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
                String realSql = sql.replace(":keys", placeholders);
                try (PreparedStatement statement = connection.prepareStatement(realSql)) {
                    resources.attach(statement);
                    statement.setQueryTimeout(timeout);
                    int parameter = 1;
                    for (Object key : batch) {
                        statement.setObject(parameter++, key);
                    }
                    readResults(statement, keyColumn, valueColumn, result, resources);
                }
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("ETL lookup failed", exception);
        } finally {
            resources.close();
        }
    }

    private void readResults(
            PreparedStatement statement,
            String keyColumn,
            String valueColumn,
            Map<JdbcValueKey, Object> result,
            CancelableJdbcResources resources)
            throws SQLException {
        try (ResultSet rows = statement.executeQuery()) {
            resources.attach(rows);
            while (rows.next()) {
                Object key = rows.getObject(keyColumn);
                Object value = rows.getObject(valueColumn);
                JdbcValueKey valueKey = JdbcValueKey.of(key);
                if (result.containsKey(valueKey)) {
                    throw new IllegalArgumentException("LOOKUP query returned a duplicate key");
                }
                result.put(valueKey, value);
            }
        }
    }

    private Number number(TransformConfig config, String key) {
        Object value = config.getProperties().get(key);
        if (value instanceof Number number) {
            return number;
        }
        if (value == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return Long.valueOf(value.toString());
    }

    private Number optionalNumber(TransformConfig config, String key, int fallback) {
        Object value = config.getProperties().get(key);
        return value == null
                ? fallback
                : value instanceof Number number ? number : Integer.valueOf(value.toString());
    }
}
