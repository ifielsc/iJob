// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.sql;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.json.JSONUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.core.execute.ExecutionHandle;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.task.TaskResult;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.instance.InstanceLogService;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;
import com.ifielsc.ijob.plugin.common.config.PluginConfigCodec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

class SqlTaskRunnerTest {
    private final List<ExecutorService> executors = new ArrayList<>();

    @AfterEach
    void shutdownExecutors() {
        executors.forEach(ExecutorService::shutdownNow);
    }

    @Test
    void transactionShouldCommitAllStatementsAndSetQueryTimeout() {
        JdbcScenario jdbc = new JdbcScenario(Step.update(1), Step.update(2));
        SqlTaskConfig config = config("update one; update two;");
        config.setQueryTimeoutSeconds(7);
        config.setMinRows(1);
        config.setMaxRows(2);

        TaskResult result =
                runner(jdbc)
                        .run(
                                spec(config),
                                context(1L, new ExecutionHandle(1L), new RecordingLogService()));

        assertAll(
                () -> assertEquals(InstanceStatus.SUCCESS, result.getStatus()),
                () -> assertEquals(List.of("update one", "update two"), jdbc.executedSql),
                () -> assertEquals(List.of(7, 7), jdbc.queryTimeouts),
                () -> assertEquals(1, jdbc.commitCount),
                () -> assertEquals(0, jdbc.rollbackCount),
                () -> assertEquals(List.of(false), jdbc.autoCommitChanges),
                () -> assertEquals(1, jdbc.connectionCloseCount));
    }

    @Test
    void statementFailureShouldRollbackAndStopFollowingStatements() {
        JdbcScenario jdbc =
                new JdbcScenario(
                        Step.update(1),
                        Step.failure("database rejected statement"),
                        Step.update(1));
        SqlTaskConfig config = config("update first; update broken; update never;");

        TaskResult result =
                runner(jdbc)
                        .run(
                                spec(config),
                                context(2L, new ExecutionHandle(2L), new RecordingLogService()));

        assertAll(
                () -> assertEquals(InstanceStatus.FAILED, result.getStatus()),
                () -> assertEquals("SQL execution failed", result.getMessage()),
                () -> assertEquals(2, jdbc.executedSql.size()),
                () -> assertEquals(0, jdbc.commitCount),
                () -> assertEquals(1, jdbc.rollbackCount),
                () -> assertEquals(1, jdbc.connectionCloseCount));
    }

    @Test
    void completedStatementsShouldReportRealProgress() {
        JdbcScenario jdbc = new JdbcScenario(Step.update(1), Step.update(1));
        RecordingInstanceService instances = new RecordingInstanceService();
        TaskContext context =
                new TaskContext(
                        11L,
                        new ExecutionHandle(11L),
                        instances,
                        new RecordingLogService(),
                        new VariableResolver());

        TaskResult result = runner(jdbc).run(spec(config("update one; update two;")), context);

        assertEquals(InstanceStatus.SUCCESS, result.getStatus());
        assertEquals(List.of(45, 90, 95), instances.progressValues);
    }

    @Test
    void scriptShouldResolveRuntimeVariablesExactlyOnce() {
        JdbcScenario jdbc = new JdbcScenario(Step.update(1));
        SqlTaskConfig config =
                config(
                        "update jobs set state='${state!}' "
                                + "where id=${id!} and note='${note!}';");
        Map<String, Object> variables =
                Map.of(
                        "state", "READY",
                        "id", 7,
                        "note", "${notExpanded!}",
                        "notExpanded", "unexpected");

        TaskResult result =
                runner(jdbc)
                        .run(
                                spec(config),
                                context(
                                        21L,
                                        new ExecutionHandle(21L),
                                        new RecordingLogService(),
                                        variables));

        assertEquals(InstanceStatus.SUCCESS, result.getStatus());
        assertEquals(
                List.of("update jobs set state='READY' where id=7 " + "and note='${notExpanded!}'"),
                jdbc.executedSql);
    }

    @Test
    void missingRequiredVariableShouldFailBeforeResourceResolution() {
        JdbcScenario jdbc = new JdbcScenario(Step.update(1));
        RecordingResolver resolver = new RecordingResolver(jdbc.dataSource());
        SqlTaskRunner runner = new SqlTaskRunner(resolver, new SqlTaskConfigValidator(), codec());

        TaskResult result =
                runner.run(
                        spec(config("select ${tenantId!};")),
                        context(22L, new ExecutionHandle(22L), new RecordingLogService()));

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertEquals("required variable missing:tenantId", result.getMessage());
        assertEquals(0, resolver.resolveCount);
    }

    @Test
    void continueOnErrorShouldWarnExecuteRemainderAndCommit() {
        JdbcScenario jdbc =
                new JdbcScenario(Step.failure("sensitive database detail"), Step.update(1));
        SqlTaskConfig config = config("update broken; update accepted;");
        config.setTransaction(false);
        config.setContinueOnError(true);
        RecordingLogService logs = new RecordingLogService();

        TaskResult result =
                runner(jdbc).run(spec(config), context(3L, new ExecutionHandle(3L), logs));

        assertAll(
                () -> assertEquals(InstanceStatus.SUCCESS, result.getStatus()),
                () -> assertEquals(2, jdbc.executedSql.size()),
                () -> assertEquals(0, jdbc.commitCount),
                () -> assertEquals(0, jdbc.rollbackCount),
                () -> assertEquals(1, logs.warnings.size()),
                () -> assertFalse(logs.warnings.getFirst().contains("sensitive database detail")));
    }

    @Test
    void queryTimeoutShouldStopRemainderAndReturnTimedOut() {
        JdbcScenario jdbc = new JdbcScenario(Step.timeout(), Step.update(1));
        SqlTaskConfig config = config("select slow; update never;");
        config.setTransaction(false);
        config.setContinueOnError(true);

        TaskResult result =
                runner(jdbc)
                        .run(
                                spec(config),
                                context(31L, new ExecutionHandle(31L), new RecordingLogService()));

        assertAll(
                () -> assertEquals(InstanceStatus.TIMED_OUT, result.getStatus()),
                () -> assertEquals("SQL query timeout", result.getMessage()),
                () -> assertEquals(1, jdbc.executedSql.size()));
    }

    @Test
    void nonTransactionalExecutionShouldEnableAutoCommit() {
        JdbcScenario jdbc = new JdbcScenario(Step.update(1));
        jdbc.autoCommit = false;
        SqlTaskConfig config = config("update accepted;");
        config.setTransaction(false);

        TaskResult result =
                runner(jdbc)
                        .run(
                                spec(config),
                                context(4L, new ExecutionHandle(4L), new RecordingLogService()));

        assertAll(
                () -> assertEquals(InstanceStatus.SUCCESS, result.getStatus()),
                () -> assertEquals(List.of(true), jdbc.autoCommitChanges),
                () -> assertEquals(0, jdbc.commitCount),
                () -> assertEquals(0, jdbc.rollbackCount));
    }

    @Test
    void resultVariableShouldSupportRowAndClearSqlNull() {
        JdbcScenario rowJdbc =
                new JdbcScenario(
                        Step.query(List.of("id", "name"), List.of(List.of(7L, "accepted"))));
        SqlTaskConfig rowConfig = config("select row;");
        rowConfig.setResultVariable("row");
        TaskContext rowContext = context(5L, new ExecutionHandle(5L), new RecordingLogService());

        TaskResult rowResult = runner(rowJdbc).run(spec(rowConfig), rowContext);

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("id", 7L);
        expected.put("name", "accepted");
        assertEquals(InstanceStatus.SUCCESS, rowResult.getStatus());
        assertEquals(expected, rowContext.getVariable("row"));

        JdbcScenario nullJdbc = new JdbcScenario(Step.queryWithNull());
        SqlTaskConfig nullConfig = config("select nullable;");
        nullConfig.setResultVariable("value");
        TaskContext nullContext = context(6L, new ExecutionHandle(6L), new RecordingLogService());
        nullContext.putVariable("value", "old");

        TaskResult nullResult = runner(nullJdbc).run(spec(nullConfig), nullContext);

        assertEquals(InstanceStatus.SUCCESS, nullResult.getStatus());
        assertFalse(nullContext.containsVariable("value"));
        assertEquals(1, nullJdbc.resultSetCloseCount);
    }

    @Test
    void affectedRowViolationShouldRollback() {
        JdbcScenario jdbc = new JdbcScenario(Step.update(1));
        SqlTaskConfig config = config("update one;");
        config.setMinRows(2);

        TaskResult result =
                runner(jdbc)
                        .run(
                                spec(config),
                                context(7L, new ExecutionHandle(7L), new RecordingLogService()));

        assertAll(
                () -> assertEquals(InstanceStatus.FAILED, result.getStatus()),
                () -> assertEquals("affected rows < minRows", result.getMessage()),
                () -> assertEquals(0, jdbc.commitCount),
                () -> assertEquals(1, jdbc.rollbackCount));
    }

    @Test
    void stopShouldCancelActiveStatementAndCloseConnection() throws Exception {
        Step blocking = Step.blocking();
        JdbcScenario jdbc = new JdbcScenario(blocking);
        SqlTaskRunner runner = runner(jdbc);
        SqlTaskConfig config = config("select blocking;");
        ExecutionHandle handle = new ExecutionHandle(8L);
        TaskContext context = context(8L, handle, new RecordingLogService());
        TaskExecutionSpec spec = spec(config);
        handle.bind(runner, spec, context);
        ExecutorService executor = register(Executors.newSingleThreadExecutor());

        Future<TaskResult> future = executor.submit(() -> runner.run(spec, context));
        assertTrue(blocking.started.await(2, TimeUnit.SECONDS));
        handle.requestStop();
        TaskResult result = future.get(2, TimeUnit.SECONDS);

        assertAll(
                () -> assertEquals(InstanceStatus.STOPPED, result.getStatus()),
                () -> assertEquals(1, jdbc.cancelCount),
                () -> assertEquals(1, jdbc.rollbackCount),
                () -> assertEquals(1, jdbc.connectionCloseCount),
                () -> assertTrue(jdbc.connectionClosed));
    }

    @Test
    void invalidConfigurationShouldFailBeforeResolvingResource() {
        JdbcScenario jdbc = new JdbcScenario();
        RecordingResolver resolver = new RecordingResolver(jdbc.dataSource());
        SqlTaskRunner runner = new SqlTaskRunner(resolver, new SqlTaskConfigValidator(), codec());
        SqlTaskConfig config = config("select 1;");
        config.setResourceId(null);

        TaskResult result =
                runner.run(
                        spec(config),
                        context(9L, new ExecutionHandle(9L), new RecordingLogService()));

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertEquals(0, resolver.resolveCount);
    }

    @Test
    void stopDuringConnectionAcquisitionShouldCloseReturnedConnection() throws Exception {
        JdbcScenario jdbc = new JdbcScenario(Step.update(1));
        CountDownLatch acquisitionStarted = new CountDownLatch(1);
        CountDownLatch releaseAcquisition = new CountDownLatch(1);
        SqlTaskRunner runner =
                new SqlTaskRunner(
                        new RecordingResolver(
                                jdbc.dataSource(acquisitionStarted, releaseAcquisition)),
                        new SqlTaskConfigValidator(),
                        codec());
        SqlTaskConfig config = config("update never;");
        ExecutionHandle handle = new ExecutionHandle(10L);
        TaskContext context = context(10L, handle, new RecordingLogService());
        TaskExecutionSpec spec = spec(config);
        handle.bind(runner, spec, context);
        ExecutorService executor = register(Executors.newSingleThreadExecutor());

        Future<TaskResult> future = executor.submit(() -> runner.run(spec, context));
        assertTrue(acquisitionStarted.await(2, TimeUnit.SECONDS));
        handle.requestStop();
        releaseAcquisition.countDown();
        TaskResult result = future.get(2, TimeUnit.SECONDS);

        assertAll(
                () -> assertEquals(InstanceStatus.STOPPED, result.getStatus()),
                () -> assertEquals(0, jdbc.executedSql.size()),
                () -> assertEquals(1, jdbc.connectionCloseCount));
    }

    private SqlTaskRunner runner(JdbcScenario jdbc) {
        return new SqlTaskRunner(
                new RecordingResolver(jdbc.dataSource()), new SqlTaskConfigValidator(), codec());
    }

    private PluginConfigCodec codec() {
        return new PluginConfigCodec(new ObjectMapper());
    }

    private SqlTaskConfig config(String script) {
        SqlTaskConfig config = new SqlTaskConfig();
        config.setResourceId(12L);
        config.setScript(script);
        return config;
    }

    private TaskExecutionSpec spec(SqlTaskConfig config) {
        return new TaskExecutionSpec(
                "sql", PluginType.SQL, JSONUtil.toJsonStr(config), 0, 0, 30, true, null);
    }

    private TaskContext context(long id, ExecutionHandle handle, InstanceLogService logs) {
        return context(id, handle, logs, Map.of());
    }

    private TaskContext context(
            long id,
            ExecutionHandle handle,
            InstanceLogService logs,
            Map<String, Object> variables) {
        return new TaskContext(
                id,
                handle,
                new RecordingInstanceService(),
                logs,
                new VariableResolver(),
                variables);
    }

    private <T extends ExecutorService> T register(T executor) {
        executors.add(executor);
        return executor;
    }

    private static final class RecordingResolver extends ResourceResolver {
        private final DataSource dataSource;
        private int resolveCount;

        private RecordingResolver(DataSource dataSource) {
            super(null);
            this.dataSource = dataSource;
        }

        @Override
        public DataSource getDataSource(Long resourceId) {
            resolveCount++;
            assertEquals(12L, resourceId);
            return dataSource;
        }
    }

    private static final class RecordingLogService extends InstanceLogService {
        private final List<String> warnings = new CopyOnWriteArrayList<>();
        private final List<String> errors = new CopyOnWriteArrayList<>();

        private RecordingLogService() {
            super(null);
        }

        @Override
        public void info(Long instanceId, String content) {}

        @Override
        public void warn(Long instanceId, String content) {
            warnings.add(content);
        }

        @Override
        public void error(Long instanceId, String content) {
            errors.add(content);
        }
    }

    private static final class RecordingInstanceService extends InstanceService {
        private final List<Integer> progressValues = new ArrayList<>();

        private RecordingInstanceService() {
            super(null, null, null, null);
        }

        @Override
        public void updateProgress(Long instanceId, Integer progress) {
            progressValues.add(progress);
        }
    }

    private static final class Step {
        private final boolean resultSet;
        private final int updateCount;
        private final List<String> labels;
        private final List<List<Object>> rows;
        private final SQLException failure;
        private final CountDownLatch started;
        private final CountDownLatch release;

        private Step(
                boolean resultSet,
                int updateCount,
                List<String> labels,
                List<List<Object>> rows,
                SQLException failure,
                CountDownLatch started,
                CountDownLatch release) {
            this.resultSet = resultSet;
            this.updateCount = updateCount;
            this.labels = labels;
            this.rows = rows;
            this.failure = failure;
            this.started = started;
            this.release = release;
        }

        private static Step update(int count) {
            return new Step(false, count, List.of(), List.of(), null, null, null);
        }

        private static Step failure(String message) {
            return new Step(false, 0, List.of(), List.of(), new SQLException(message), null, null);
        }

        private static Step timeout() {
            return new Step(
                    false,
                    0,
                    List.of(),
                    List.of(),
                    new SQLTimeoutException("driver query timeout"),
                    null,
                    null);
        }

        private static Step query(List<String> labels, List<List<Object>> rows) {
            return new Step(true, -1, labels, rows, null, null, null);
        }

        private static Step queryWithNull() {
            List<Object> row = new ArrayList<>();
            row.add(null);
            return query(List.of("value"), List.of(row));
        }

        private static Step blocking() {
            return new Step(
                    false,
                    0,
                    List.of(),
                    List.of(),
                    new SQLException("cancelled"),
                    new CountDownLatch(1),
                    new CountDownLatch(1));
        }
    }

    private static final class JdbcScenario {
        private final Deque<Step> steps = new ArrayDeque<>();
        private final List<String> executedSql = new CopyOnWriteArrayList<>();
        private final List<Integer> queryTimeouts = new CopyOnWriteArrayList<>();
        private final List<Boolean> autoCommitChanges = new CopyOnWriteArrayList<>();
        private volatile boolean autoCommit = true;
        private volatile boolean connectionClosed;
        private int commitCount;
        private int rollbackCount;
        private int connectionCloseCount;
        private int cancelCount;
        private int resultSetCloseCount;

        private JdbcScenario(Step... steps) {
            this.steps.addAll(Arrays.asList(steps));
        }

        private DataSource dataSource() {
            return dataSource(null, null);
        }

        private DataSource dataSource(
                CountDownLatch acquisitionStarted, CountDownLatch releaseAcquisition) {
            return proxy(
                    DataSource.class,
                    (proxy, method, args) -> {
                        if (method.getName().equals("getConnection")) {
                            if (acquisitionStarted != null) {
                                acquisitionStarted.countDown();
                                releaseAcquisition.await();
                            }
                            return connection();
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private Connection connection() {
            return proxy(
                    Connection.class,
                    (proxy, method, args) -> {
                        return switch (method.getName()) {
                            case "createStatement" -> statement();
                            case "getAutoCommit" -> autoCommit;
                            case "setAutoCommit" -> {
                                autoCommit = (boolean) args[0];
                                autoCommitChanges.add(autoCommit);
                                yield null;
                            }
                            case "commit" -> {
                                requireOpen();
                                commitCount++;
                                yield null;
                            }
                            case "rollback" -> {
                                requireOpen();
                                rollbackCount++;
                                yield null;
                            }
                            case "close" -> {
                                if (!connectionClosed) {
                                    connectionClosed = true;
                                    connectionCloseCount++;
                                }
                                yield null;
                            }
                            case "isClosed" -> connectionClosed;
                            default -> defaultValue(method.getReturnType());
                        };
                    });
        }

        private Statement statement() {
            class StatementState {
                private Step step;
                private boolean closed;
            }
            StatementState state = new StatementState();
            return proxy(
                    Statement.class,
                    (proxy, method, args) -> {
                        return switch (method.getName()) {
                            case "setQueryTimeout" -> {
                                queryTimeouts.add((Integer) args[0]);
                                yield null;
                            }
                            case "execute" -> {
                                state.step = steps.removeFirst();
                                executedSql.add((String) args[0]);
                                if (state.step.started != null) {
                                    state.step.started.countDown();
                                    state.step.release.await();
                                }
                                if (state.step.failure != null) {
                                    throw state.step.failure;
                                }
                                yield state.step.resultSet;
                            }
                            case "getResultSet" -> resultSet(state.step);
                            case "getUpdateCount" -> state.step.updateCount;
                            case "cancel" -> {
                                cancelCount++;
                                if (state.step != null && state.step.release != null) {
                                    state.step.release.countDown();
                                }
                                yield null;
                            }
                            case "close" -> {
                                state.closed = true;
                                yield null;
                            }
                            case "isClosed" -> state.closed;
                            default -> defaultValue(method.getReturnType());
                        };
                    });
        }

        private ResultSet resultSet(Step step) {
            class ResultState {
                private int rowIndex = -1;
            }
            ResultState state = new ResultState();
            ResultSetMetaData metadata =
                    proxy(
                            ResultSetMetaData.class,
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "getColumnCount" -> step.labels.size();
                                        case "getColumnLabel" ->
                                                step.labels.get((Integer) args[0] - 1);
                                        default -> defaultValue(method.getReturnType());
                                    });
            return proxy(
                    ResultSet.class,
                    (proxy, method, args) -> {
                        return switch (method.getName()) {
                            case "getMetaData" -> metadata;
                            case "next" -> ++state.rowIndex < step.rows.size();
                            case "getObject" ->
                                    step.rows.get(state.rowIndex).get((Integer) args[0] - 1);
                            case "close" -> {
                                resultSetCloseCount++;
                                yield null;
                            }
                            default -> defaultValue(method.getReturnType());
                        };
                    });
        }

        private void requireOpen() throws SQLException {
            if (connectionClosed) {
                throw new SQLException("connection closed");
            }
        }

        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return type.cast(
                    Proxy.newProxyInstance(
                            type.getClassLoader(),
                            new Class<?>[] {type},
                            new ObjectMethodHandler(handler)));
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) {
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == char.class) {
                return '\0';
            }
            return 0;
        }
    }

    private static final class ObjectMethodHandler implements InvocationHandler {
        private final InvocationHandler delegate;

        private ObjectMethodHandler(InvocationHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return switch (method.getName()) {
                case "toString" -> "FakeJdbcProxy";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> delegate.invoke(proxy, method, args);
            };
        }
    }
}
