// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.sql;

import cn.hutool.core.util.StrUtil;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.exception.StopException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.core.execute.TimeoutExceptionClassifier;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.task.TaskResult;
import com.ifielsc.ijob.core.execute.task.TaskRunner;
import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;
import com.ifielsc.ijob.plugin.common.config.PluginConfigCodec;
import com.ifielsc.ijob.plugin.common.jdbc.JdbcColumnValues;
import com.ifielsc.ijob.plugin.common.result.PluginMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "app.plugin.sql",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SqlTaskRunner implements TaskRunner {
    private final ResourceResolver resourceResolver;
    private final SqlTaskConfigValidator configValidator;
    private final PluginConfigCodec configCodec;

    @Override
    public PluginType supportType() {
        return PluginType.SQL;
    }

    @Override
    public TaskResult run(TaskExecutionSpec spec, TaskContext context) {
        boolean configValidated = false;
        try {
            SqlTaskConfig config =
                    configCodec.read(spec.getPluginConfig(), SqlTaskConfig.class, "SQL");
            if (config != null) {
                config.setScript(context.resolve(config.getScript()));
            }
            configValidator.validate(config);
            configValidated = true;
            DataSource dataSource = resourceResolver.getDataSource(config.getResourceId());

            executeScript(dataSource, config, context);
            return TaskResult.success();
        } catch (StopException e) {
            return TaskResult.stop();
        } catch (Exception e) {
            try {
                context.checkStop();
            } catch (StopException stopException) {
                return TaskResult.stop();
            }
            context.logError("SQL execution failed type=" + e.getClass().getSimpleName());
            log.error(
                    "SQL execution failed, instanceId={}",
                    context.getInstanceId(),
                    com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(e));
            if (TimeoutExceptionClassifier.isTimeout(e)) {
                return TaskResult.timeout(PluginMessage.SQL_TIMEOUT);
            }
            if (e instanceof AffectedRowsException) {
                return TaskResult.failure(e, PluginMessage.SQL_FAILED);
            }
            boolean actionableConfigFailure =
                    !configValidated
                            && (e instanceof IllegalArgumentException
                                    || e instanceof BusinessException business
                                            && business.getCode() == ResultCode.PARAM_ERROR);
            return actionableConfigFailure
                    ? TaskResult.failure(e, PluginMessage.SQL_FAILED)
                    : TaskResult.fail(PluginMessage.SQL_FAILED);
        }
    }

    @Override
    public void stop(TaskExecutionSpec spec, TaskContext context) {
        context.cleanupResources();
    }

    private void executeScript(DataSource dataSource, SqlTaskConfig config, TaskContext context)
            throws SQLException {
        List<String> sqls = SqlScriptParser.parse(config.getScript(), config.getDelimiter());
        context.logInfo(
                "SQL execution started, statements="
                        + sqls.size()
                        + ", transaction="
                        + config.isTransaction());

        Connection conn = dataSource.getConnection();
        SqlExecutionResource resource = new SqlExecutionResource(conn, config.isTransaction());
        context.registerResource(resource);
        try {
            context.checkStop();
            if (config.isTransaction()) {
                executeWithTransaction(conn, sqls, config, context, resource);
            } else {
                executeWithoutTransaction(conn, sqls, config, context, resource);
            }
        } finally {
            resource.close();
        }
        context.progress(95);
    }

    private void executeWithTransaction(
            Connection conn,
            List<String> sqls,
            SqlTaskConfig config,
            TaskContext context,
            SqlExecutionResource resource)
            throws SQLException {
        conn.setAutoCommit(false);
        context.logInfo("SQL transaction started");
        try {
            executeSqls(conn, sqls, config, context, resource);
            context.checkStop();
            conn.commit();
            resource.markTransactionComplete();
            context.logInfo("SQL transaction committed");
        } catch (Exception e) {
            try {
                resource.rollbackTransaction();
                context.logWarn("SQL transaction rolled back");
            } catch (SQLException rollbackException) {
                context.logError("SQL transaction rollback failed");
                e.addSuppressed(rollbackException);
            }
            throw e;
        }
    }

    private void executeWithoutTransaction(
            Connection conn,
            List<String> sqls,
            SqlTaskConfig config,
            TaskContext context,
            SqlExecutionResource resource)
            throws SQLException {
        if (!conn.getAutoCommit()) {
            conn.setAutoCommit(true);
        }
        executeSqls(conn, sqls, config, context, resource);
    }

    private void executeSqls(
            Connection conn,
            List<String> sqls,
            SqlTaskConfig config,
            TaskContext context,
            SqlExecutionResource resource)
            throws SQLException {
        int statementCount = sqls.size();
        for (int index = 0; index < statementCount; index++) {
            String sql = sqls.get(index);
            context.checkExecution();

            try (Statement stmt = conn.createStatement()) {
                resource.bindStatement(stmt);
                context.checkStop();
                long start = System.currentTimeMillis();

                stmt.setQueryTimeout(config.getQueryTimeoutSeconds());
                boolean hasResultSet = stmt.execute(sql);
                context.checkStop();
                if (hasResultSet) {
                    try (ResultSet resultSet = stmt.getResultSet()) {
                        if (resultSet == null) {
                            throw new SQLException("SQL result set is unavailable");
                        }
                        handleResultSet(resultSet, config, context);
                    }
                } else {
                    int affected = stmt.getUpdateCount();
                    validateAffectedRows(affected, config);
                }

                context.logInfo(
                        String.format(
                                "SQL statement completed, durationMs=%d",
                                System.currentTimeMillis() - start));
            } catch (Exception ex) {
                context.checkStop();
                if (TimeoutExceptionClassifier.isTimeout(ex)) {
                    throw ex;
                }
                if (!config.isContinueOnError()) {
                    throw ex;
                }

                context.logWarn(
                        "SQL statement failed; continuing as configured, type="
                                + ex.getClass().getSimpleName());
            } finally {
                resource.clearStatement();
            }
            context.progress(90 * (index + 1) / statementCount);
        }
    }

    private void handleResultSet(ResultSet rs, SqlTaskConfig config, TaskContext context)
            throws SQLException {
        String variable = config.getResultVariable();
        if (StrUtil.isBlank(variable)) {
            return;
        }

        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        if (!rs.next()) {
            context.removeVariable(variable);
            return;
        }

        if (columnCount == 1) {
            putNullableVariable(
                    context,
                    variable,
                    JdbcColumnValues.readVariable(rs, 1, metaData.getColumnType(1)));
            return;
        }

        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= columnCount; i++) {
            row.put(
                    metaData.getColumnLabel(i),
                    JdbcColumnValues.readVariable(rs, i, metaData.getColumnType(i)));
        }
        context.putVariable(variable, row);
    }

    private void putNullableVariable(TaskContext context, String variable, Object value) {
        if (value == null) {
            context.removeVariable(variable);
        } else {
            context.putVariable(variable, value);
        }
    }

    private void validateAffectedRows(int affected, SqlTaskConfig config) {
        if (config.getMinRows() != null && affected < config.getMinRows()) {
            throw new AffectedRowsException("affected rows < minRows");
        }

        if (config.getMaxRows() != null && affected > config.getMaxRows()) {
            throw new AffectedRowsException("affected rows > maxRows");
        }
    }

    private static final class AffectedRowsException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        private AffectedRowsException(String message) {
            super(message);
        }
    }
}
