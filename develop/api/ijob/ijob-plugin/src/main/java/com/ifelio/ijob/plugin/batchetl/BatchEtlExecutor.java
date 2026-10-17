package com.ifelio.ijob.plugin.batchetl;

import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.resource.ResourceResolver;
import com.ifelio.ijob.plugin.batchetl.config.BatchEtlTaskConfig;
import com.ifelio.ijob.plugin.batchetl.metadata.BatchTablePlan;
import com.ifelio.ijob.plugin.batchetl.metadata.BatchTablePlanner;
import com.ifelio.ijob.plugin.common.jdbc.CancelableJdbcResources;
import com.ifelio.ijob.plugin.common.jdbc.JdbcIdentifierQuoter;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

@Component
@RequiredArgsConstructor
public class BatchEtlExecutor {
    private final ResourceResolver resourceResolver;
    private final BatchTablePlanner tablePlanner;
    private final BatchTableMigrator tableMigrator;

    public BatchEtlResult execute(BatchEtlTaskConfig config, TaskContext context) {
        CancelableJdbcResources sourceResources = new CancelableJdbcResources();
        CancelableJdbcResources targetResources = new CancelableJdbcResources();
        context.registerResource(sourceResources);
        context.registerResource(targetResources);
        Connection targetConnection = null;
        try {
            DataSource sourceDataSource =
                    resourceResolver.getDataSource(config.getSourceResourceId());
            DataSource targetDataSource =
                    resourceResolver.getDataSource(config.getTargetResourceId());
            Connection sourceConnection = sourceDataSource.getConnection();
            sourceResources.attach(sourceConnection);
            sourceConnection.setAutoCommit(false);
            targetConnection = targetDataSource.getConnection();
            targetResources.attach(targetConnection);
            targetConnection.setAutoCommit(false);
            List<BatchTablePlan> plans =
                    tablePlanner.plan(sourceConnection, targetConnection, config);
            context.logInfo(
                    "Batch ETL plan prepared, tables="
                            + plans.size()
                            + ", clearBeforeImport="
                            + config.isClearBeforeImport());
            context.checkStop();
            clearTargetsIfRequired(targetConnection, targetResources, plans, config, context);
            List<BatchEtlResult.TableResult> results = new ArrayList<>();
            long totalRows = 0;
            for (int index = 0; index < plans.size(); index++) {
                context.logInfo("Batch ETL table migration started, tableIndex=" + (index + 1));
                context.checkStop();
                context.checkPause();
                long remaining = config.getExecution().getMaxTotalRows() - totalRows;
                BatchEtlResult.TableResult result =
                        tableMigrator.migrate(
                                sourceConnection,
                                targetConnection,
                                sourceResources,
                                targetResources,
                                plans.get(index),
                                config,
                                context,
                                remaining);
                results.add(result);
                totalRows += result.extracted();
                context.logInfo(
                        "Batch ETL table migration completed, tableIndex="
                                + (index + 1)
                                + ", extracted="
                                + result.extracted());
                context.progress(Math.min(99, (index + 1) * 100 / plans.size()));
            }
            BatchTableMigrator.commit(targetConnection, context);
            context.logInfo("Batch ETL final transaction committed");
            return summarize(results);
        } catch (Exception exception) {
            rollback(targetConnection, exception);
            throw exception instanceof IllegalArgumentException invalid
                    ? invalid
                    : new IllegalStateException("Batch ETL execution failed", exception);
        } finally {
            targetResources.close();
            sourceResources.close();
        }
    }

    private void clearTargetsIfRequired(
            Connection connection,
            CancelableJdbcResources resources,
            List<BatchTablePlan> plans,
            BatchEtlTaskConfig config,
            TaskContext context)
            throws SQLException {
        if (!config.isClearBeforeImport()) {
            return;
        }
        context.logWarn(
                "Batch ETL target clearing started, tables="
                        + plans.size()
                        + ", mode="
                        + config.getClearMode());
        JdbcIdentifierQuoter quoter = JdbcIdentifierQuoter.from(connection.getMetaData());
        for (BatchTablePlan plan : plans.reversed()) {
            context.checkStop();
            context.checkPause();
            String operation =
                    config.getClearMode() == BatchEtlTaskConfig.ClearMode.TRUNCATE
                            ? "TRUNCATE TABLE "
                            : "DELETE FROM ";
            String sql = operation + quoter.qualify(plan.targetSchema(), plan.targetTable());
            try (Statement statement = connection.createStatement()) {
                resources.attach(statement);
                statement.setQueryTimeout(config.getExecution().getQueryTimeoutSeconds());
                if (config.getClearMode() == BatchEtlTaskConfig.ClearMode.TRUNCATE)
                    context.putVariable("batchEtl.nonTransactionalClearAttempted", true);
                statement.executeUpdate(sql);
            }
        }
    }

    private BatchEtlResult summarize(List<BatchEtlResult.TableResult> results) {
        long extracted = 0;
        long inserted = 0;
        long updated = 0;
        long skipped = 0;
        for (BatchEtlResult.TableResult result : results) {
            extracted += result.extracted();
            inserted += result.inserted();
            updated += result.updated();
            skipped += result.skipped();
        }
        return new BatchEtlResult(extracted, inserted, updated, skipped, List.copyOf(results));
    }

    private void rollback(Connection connection, Exception primary) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            primary.addSuppressed(rollbackFailure);
        }
    }
}
