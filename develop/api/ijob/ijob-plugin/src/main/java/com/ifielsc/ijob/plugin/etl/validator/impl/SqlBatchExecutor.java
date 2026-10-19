// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.validator.impl;

import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.plugin.common.jdbc.CancelableJdbcResources;
import com.ifielsc.ijob.plugin.etl.model.EtlContext;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.sql.DataSource;

@Component
@RequiredArgsConstructor
public class SqlBatchExecutor {
    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final int MAX_BATCH_SIZE = 2000;

    private final ResourceResolver resourceResolver;

    public List<Object> queryIn(
            long resourceId, String sql, Collection<?> keys, int queryTimeoutSeconds) {
        return queryIn(resourceId, sql, keys, queryTimeoutSeconds, DEFAULT_BATCH_SIZE, null);
    }

    public List<Object> queryIn(
            long resourceId,
            String sql,
            Collection<?> keys,
            int queryTimeoutSeconds,
            int batchSize,
            EtlContext context) {
        if (keys.isEmpty()) {
            return List.of();
        }
        if (!sql.contains(":keys")) {
            throw new IllegalArgumentException("SQL validation query must contain :keys");
        }
        if (batchSize <= 0 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "SQL validation batchSize must be between 1 and 2000");
        }
        List<?> keyList = new ArrayList<>(keys);
        DataSource dataSource = resourceResolver.getDataSource(resourceId);
        CancelableJdbcResources resources = new CancelableJdbcResources();
        register(context, resources);
        try {
            check(context);
            Connection connection = dataSource.getConnection();
            resources.attach(connection);
            List<Object> result = new ArrayList<>();
            for (int from = 0; from < keyList.size(); from += batchSize) {
                check(context);
                List<?> batch = keyList.subList(from, Math.min(keyList.size(), from + batchSize));
                String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
                String realSql = sql.replace(":keys", placeholders);
                try (PreparedStatement statement = connection.prepareStatement(realSql)) {
                    resources.attach(statement);
                    statement.setQueryTimeout(queryTimeoutSeconds);
                    int parameter = 1;
                    for (Object key : batch) {
                        statement.setObject(parameter++, key);
                    }
                    try (ResultSet rows = statement.executeQuery()) {
                        resources.attach(rows);
                        while (rows.next()) {
                            result.add(rows.getObject(1));
                        }
                    }
                }
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("ETL SQL validation query failed", exception);
        } finally {
            resources.close();
        }
    }

    public boolean queryGlobal(long resourceId, String sql, int queryTimeoutSeconds) {
        return queryGlobal(resourceId, sql, queryTimeoutSeconds, null);
    }

    public boolean queryGlobal(
            long resourceId, String sql, int queryTimeoutSeconds, EtlContext context) {
        DataSource dataSource = resourceResolver.getDataSource(resourceId);
        CancelableJdbcResources resources = new CancelableJdbcResources();
        register(context, resources);
        try {
            check(context);
            Connection connection = dataSource.getConnection();
            resources.attach(connection);
            PreparedStatement statement = connection.prepareStatement(sql);
            resources.attach(statement);
            statement.setQueryTimeout(queryTimeoutSeconds);
            try (ResultSet rows = statement.executeQuery()) {
                resources.attach(rows);
                return rows.next() && rows.getLong(1) > 0;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("ETL global validation query failed", exception);
        } finally {
            resources.close();
        }
    }

    private void register(EtlContext context, CancelableJdbcResources resources) {
        if (context != null) {
            context.getTaskContext().registerResource(resources);
        }
    }

    private void check(EtlContext context) {
        if (context != null) {
            context.checkControl();
        }
    }
}
