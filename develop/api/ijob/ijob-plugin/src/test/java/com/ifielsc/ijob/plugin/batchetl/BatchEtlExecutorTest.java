package com.ifielsc.ijob.plugin.batchetl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifielsc.ijob.core.execute.ExecutionHandle;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.plugin.batchetl.config.BatchEtlTaskConfig;
import com.ifielsc.ijob.plugin.batchetl.metadata.BatchTablePlan;
import com.ifielsc.ijob.plugin.batchetl.metadata.BatchTablePlanner;
import com.ifielsc.ijob.plugin.common.jdbc.CancelableJdbcResources;
import com.ifielsc.ijob.plugin.support.RecordingInstanceLogs;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

class BatchEtlExecutorTest {
    @Test
    void shouldExecuteExplicitTruncateInReverseTableOrder() {
        List<String> sql = new ArrayList<>();
        Connection source = connection(sql);
        Connection target = connection(sql);
        List<BatchTablePlan> plans = List.of(plan("A", "X_A"), plan("B", "X_B"));
        ResourceResolver resolver =
                new ResourceResolver(null) {
                    @Override
                    public DataSource getDataSource(Long resourceId) {
                        return dataSource(resourceId == 1L ? source : target);
                    }
                };
        BatchTablePlanner planner =
                new BatchTablePlanner(null) {
                    @Override
                    public List<BatchTablePlan> plan(
                            Connection sourceConnection,
                            Connection targetConnection,
                            BatchEtlTaskConfig config) {
                        return plans;
                    }
                };
        BatchTableMigrator migrator =
                new BatchTableMigrator(null) {
                    @Override
                    public BatchEtlResult.TableResult migrate(
                            Connection sourceConnection,
                            Connection targetConnection,
                            CancelableJdbcResources sourceResources,
                            CancelableJdbcResources targetResources,
                            BatchTablePlan plan,
                            BatchEtlTaskConfig config,
                            TaskContext context,
                            long remainingRows) {
                        return new BatchEtlResult.TableResult(
                                plan.sourceTable(), plan.targetTable(), 0, 0, 0, 0);
                    }
                };
        BatchEtlTaskConfig config = new BatchEtlTaskConfig();
        config.setSourceResourceId(1L);
        config.setTargetResourceId(2L);
        config.setClearBeforeImport(true);
        config.setClearMode(BatchEtlTaskConfig.ClearMode.TRUNCATE);

        new BatchEtlExecutor(resolver, planner, migrator).execute(config, context());

        assertEquals(List.of("TRUNCATE TABLE `DW`.`X_B`", "TRUNCATE TABLE `DW`.`X_A`"), sql);
    }

    private BatchTablePlan plan(String source, String target) {
        return new BatchTablePlan("APP", source, "DW", target, List.of(), List.of(), null);
    }

    private TaskContext context() {
        InstanceService instances =
                new InstanceService(null, null, null, null) {
                    @Override
                    public void updateProgress(Long instanceId, Integer progress) {
                        // Progress is not relevant to this SQL assertion.
                    }
                };
        return new TaskContext(
                1L,
                new ExecutionHandle(1L),
                instances,
                new RecordingInstanceLogs(),
                new VariableResolver());
    }

    private DataSource dataSource(Connection connection) {
        return (DataSource)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {DataSource.class},
                        (proxy, method, args) ->
                                method.getName().equals("getConnection")
                                        ? connection
                                        : defaultValue(method.getReturnType()));
    }

    private Connection connection(List<String> sql) {
        DatabaseMetaData metadata =
                (DatabaseMetaData)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {DatabaseMetaData.class},
                                (proxy, method, args) ->
                                        method.getName().equals("getIdentifierQuoteString")
                                                ? "`"
                                                : defaultValue(method.getReturnType()));
        return (Connection)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {Connection.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "getMetaData" -> metadata;
                                    case "createStatement" -> statement(sql);
                                    default -> defaultValue(method.getReturnType());
                                });
    }

    private Statement statement(List<String> sql) {
        return (Statement)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {Statement.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("executeUpdate")) {
                                sql.add((String) args[0]);
                                return 0;
                            }
                            return defaultValue(method.getReturnType());
                        });
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }
}
