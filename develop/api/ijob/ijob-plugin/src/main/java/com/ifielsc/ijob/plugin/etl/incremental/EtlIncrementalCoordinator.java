// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.incremental;

import com.ifielsc.ijob.core.etl.EtlCheckpointSnapshot;
import com.ifielsc.ijob.core.etl.EtlCheckpointStore;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.plugin.common.jdbc.CancelableJdbcResources;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;

import javax.sql.DataSource;

@Component
@RequiredArgsConstructor
public class EtlIncrementalCoordinator {
    private final EtlCheckpointStore checkpointStore;
    private final ResourceResolver resourceResolver;

    public EtlIncrementalSession open(EtlTaskConfig config, TaskContext context) {
        EtlTaskConfig.IncrementalConfig incremental = config.getIncremental();
        if (incremental == null || !incremental.isEnabled()) {
            return EtlIncrementalSession.disabled();
        }
        EtlCheckpointSnapshot snapshot =
                checkpointStore.loadOrCreate(
                        context.getInstanceId(),
                        incremental.getCheckpointKey(),
                        fingerprint(config),
                        incremental.getWatermarkType(),
                        incremental.getInitialWatermark(),
                        incremental.getInitialTieBreakerValues());
        Object upperBound = queryUpperBound(config, context);
        if (upperBound == null) {
            throw new IllegalArgumentException("ETL incremental upper bound must not be null");
        }
        return new EtlIncrementalSession(
                checkpointStore, snapshot, incremental, context.getInstanceId(), upperBound);
    }

    private Object queryUpperBound(EtlTaskConfig config, TaskContext context) {
        DataSource dataSource = resourceResolver.getDataSource(config.getSource().getResourceId());
        context.checkStop();
        context.checkPause();
        String sql = context.resolve(config.getIncremental().getUpperBoundSql());
        CancelableJdbcResources resource = new CancelableJdbcResources();
        context.registerResource(resource);
        try {
            Connection connection = dataSource.getConnection();
            resource.attach(connection);
            connection.setAutoCommit(true);
            executeBeforeSql(connection, config, context, resource);
            PreparedStatement statement = connection.prepareStatement(sql);
            resource.attach(statement);
            statement.setQueryTimeout(config.getExecution().getQueryTimeoutSeconds());
            try (ResultSet rows = statement.executeQuery()) {
                resource.attach(rows);
                if (!rows.next()) {
                    throw new IllegalArgumentException(
                            "ETL incremental upper bound query returned no row");
                }
                Object result = rows.getObject(1);
                if (rows.next()) {
                    throw new IllegalArgumentException(
                            "ETL incremental upper bound query returned " + "multiple rows");
                }
                context.checkStop();
                return result;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("ETL incremental upper bound query failed", exception);
        } finally {
            resource.close();
        }
    }

    private void executeBeforeSql(
            Connection connection,
            EtlTaskConfig config,
            TaskContext context,
            CancelableJdbcResources resource)
            throws SQLException {
        for (String script : config.getSource().getBeforeSql()) {
            context.checkStop();
            context.checkPause();
            try (Statement statement = connection.createStatement()) {
                resource.attach(statement);
                statement.setQueryTimeout(config.getExecution().getQueryTimeoutSeconds());
                statement.execute(context.resolve(script));
            }
        }
    }

    public static String fingerprint(EtlTaskConfig config) {
        EtlTaskConfig.IncrementalConfig incremental = config.getIncremental();
        String source =
                config.getSource().getResourceId()
                        + "\n"
                        + config.getSource().getSql()
                        + "\n"
                        + config.getSource().getBeforeSql()
                        + "\n"
                        + config.getSource().getParameters()
                        + "\n"
                        + incremental.getWatermarkField()
                        + "\n"
                        + incremental.getWatermarkType()
                        + "\n"
                        + String.join(",", incremental.getTieBreakerFields())
                        + "\n"
                        + incremental.getUpperBoundSql();
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
