package com.ifielsc.ijob.plugin.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.json.JSONUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.core.execute.ExecutionHandle;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.task.TaskResult;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.instance.InstanceLogService;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;
import com.ifielsc.ijob.plugin.common.config.PluginConfigCodec;
import com.ifielsc.ijob.plugin.push.compress.CompressionRegistry;
import com.ifielsc.ijob.plugin.push.compress.GzipCompressionService;
import com.ifielsc.ijob.plugin.push.factory.PushPublisherFactory;
import com.ifielsc.ijob.plugin.push.publisher.PublisherChainBuilder;
import com.ifielsc.ijob.plugin.push.publisher.PushPublisher;
import com.ifielsc.ijob.plugin.push.resolver.PushPublisherResolver;
import com.ifielsc.ijob.plugin.push.resolver.PushTaskPublishConfig;
import com.ifielsc.ijob.plugin.push.retry.RetryExecutor;
import com.ifielsc.ijob.plugin.push.serializer.JsonMessageSerializer;
import com.ifielsc.ijob.plugin.push.serializer.MessageSerializerRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

class PushTaskRunnerTest {
    private final List<ExecutorService> executors = new ArrayList<>();

    @AfterEach
    void cleanup() {
        executors.forEach(ExecutorService::shutdownNow);
    }

    @Test
    void rowsShouldPublishThroughConfiguredChainAndReleaseResources() {
        RecordingPublisher publisher = new RecordingPublisher();
        RecordingResolver resources =
                new RecordingResolver(
                        dataSource(
                                List.of(
                                        List.of(1L, "first"),
                                        List.of(2L, "second"),
                                        List.of(3L, "third"))));
        PushTaskConfig config = config();
        config.setBatchSize(2);
        config.setCompression("gzip");
        config.setRetryTimes(0);
        TaskContext context = context(1L, new ExecutionHandle(1L), new RecordingLogService());

        TaskResult result = runner(resources, publisher).run(spec(config), context);

        assertEquals(InstanceStatus.SUCCESS, result.getStatus());
        assertEquals(3, publisher.messages.size());
        assertEquals(1, resources.resolveCount.get());
        assertEquals(1, publisher.closeCount.get());
        assertTrue(
                publisher.messages.stream()
                        .allMatch(message -> message.getSerializedPayload() != null));
        assertEquals(3L, context.getVariable("push.messageCount"));
        assertEquals(3L, context.getVariable("push.attemptedCount"));
        assertEquals(3L, context.getVariable("push.successCount"));
        assertEquals(0L, context.getVariable("push.failedCount"));
        assertTrue((Long) context.getVariable("push.payloadBytes") > 0);
        assertTrue((Long) context.getVariable("push.elapsedMillis") >= 0);
    }

    @Test
    void oversizedSerializedMessageShouldFailBeforeTargetPublish() {
        RecordingPublisher publisher = new RecordingPublisher();
        PushTaskConfig config = config();
        config.setRetryTimes(0);
        config.setMaxMessageBytes(1024);
        config.setMaxTotalPayloadBytes(4096L);

        TaskContext context = context(5L, new ExecutionHandle(5L), new RecordingLogService());
        TaskResult result =
                runner(
                                new RecordingResolver(
                                        dataSource(List.of(List.of(1L, "x".repeat(2048))))),
                                publisher)
                        .run(spec(config), context);

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertEquals(0, publisher.messages.size());
        assertEquals(1L, context.getVariable("push.attemptedCount"));
        assertEquals(0L, context.getVariable("push.messageCount"));
        assertEquals(1L, context.getVariable("push.failedCount"));
    }

    @Test
    void asynchronousFailureShouldBeGenericAndSensitiveSafe() {
        RecordingPublisher publisher = new RecordingPublisher();
        publisher.action =
                message ->
                        CompletableFuture.failedFuture(
                                new IllegalStateException("password=secret-value"));
        RecordingLogService logs = new RecordingLogService();
        PushTaskConfig config = config();
        config.setRetryTimes(0);

        TaskContext context = context(2L, new ExecutionHandle(2L), logs);
        TaskResult result =
                runner(new RecordingResolver(dataSource(List.of(List.of(1L, "first")))), publisher)
                        .run(spec(config), context);

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertEquals("push failed", result.getMessage());
        assertFalse(logs.error.contains("secret-value"));
        assertTrue(logs.error.contains("IllegalStateException"));
        assertEquals(1, publisher.closeCount.get());
        assertEquals(1L, context.getVariable("push.attemptedCount"));
        assertEquals(0L, context.getVariable("push.successCount"));
        assertEquals(1L, context.getVariable("push.failedCount"));
    }

    @Test
    void stopShouldCancelPendingPublishAndClosePublisher() throws Exception {
        RecordingPublisher publisher = new RecordingPublisher();
        CompletableFuture<Void> pending = new CompletableFuture<>();
        publisher.action =
                message -> {
                    publisher.started.countDown();
                    return pending;
                };
        PushTaskRunner runner =
                runner(new RecordingResolver(dataSource(List.of(List.of(1L, "first")))), publisher);
        PushTaskConfig config = config();
        config.setRetryTimes(0);
        TaskExecutionSpec spec = spec(config);
        ExecutionHandle handle = new ExecutionHandle(3L);
        TaskContext context = context(3L, handle, new RecordingLogService());
        handle.bind(runner, spec, context);
        ExecutorService executor = register(Executors.newSingleThreadExecutor());

        Future<TaskResult> future = executor.submit(() -> runner.run(spec, context));
        assertTrue(publisher.started.await(2, TimeUnit.SECONDS));
        handle.requestStop();
        TaskResult result = future.get(2, TimeUnit.SECONDS);

        assertEquals(InstanceStatus.STOPPED, result.getStatus());
        assertTrue(pending.isCancelled());
        assertEquals(1, publisher.closeCount.get());
    }

    @Test
    void invalidConfigShouldFailBeforeResolvingAnyResource() {
        RecordingPublisher publisher = new RecordingPublisher();
        RecordingResolver resources = new RecordingResolver(dataSource(List.of()));
        PushTaskConfig config = config();
        config.setMaxInflight(0);

        TaskResult result =
                runner(resources, publisher)
                        .run(
                                spec(config),
                                context(4L, new ExecutionHandle(4L), new RecordingLogService()));

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertEquals(0, resources.resolveCount.get());
        assertEquals(0, publisher.closeCount.get());
    }

    @Test
    void queryAndTargetNameShouldResolveBeforeValidation() {
        RecordingPublisher publisher = new RecordingPublisher();
        AtomicReference<String> preparedSql = new AtomicReference<>();
        RecordingResolver resources =
                new RecordingResolver(dataSource(List.of(List.of(1L, "first")), preparedSql));
        PushTaskConfig config = config();
        config.setSql("select id, name from source " + "where tenant_id=${tenantId!}");
        config.getTarget().setProperties(Map.of("topic", "jobs-${region!}"));

        TaskResult result =
                runner(resources, publisher)
                        .run(
                                spec(config),
                                context(
                                        7L,
                                        new ExecutionHandle(7L),
                                        new RecordingLogService(),
                                        Map.of("tenantId", 9, "region", "east")));

        assertEquals(InstanceStatus.SUCCESS, result.getStatus());
        assertEquals("select id, name from source where tenant_id=9", preparedSql.get());
        assertEquals("jobs-east", publisher.createdConfig.getProperties().get("topic"));
    }

    @Test
    void invalidResolvedTargetAndMissingVariableShouldFailEarly() {
        RecordingPublisher publisher = new RecordingPublisher();
        RecordingResolver resources = new RecordingResolver(dataSource(List.of()));
        PushTaskConfig config = config();
        config.getTarget().setProperties(Map.of("topic", "${topic!}"));

        TaskResult invalid =
                runner(resources, publisher)
                        .run(
                                spec(config),
                                context(
                                        8L,
                                        new ExecutionHandle(8L),
                                        new RecordingLogService(),
                                        Map.of("topic", "bad topic")));
        TaskResult missing =
                runner(resources, publisher)
                        .run(
                                spec(config),
                                context(9L, new ExecutionHandle(9L), new RecordingLogService()));

        assertEquals(InstanceStatus.FAILED, invalid.getStatus());
        assertTrue(invalid.getMessage().contains("topic"));
        assertEquals("required variable missing:topic", missing.getMessage());
        assertEquals(0, resources.resolveCount.get());
        assertEquals(0, publisher.closeCount.get());
    }

    @Test
    void resolvedWriteQueryShouldFailBeforeResourceResolution() {
        RecordingPublisher publisher = new RecordingPublisher();
        RecordingResolver resources = new RecordingResolver(dataSource(List.of()));
        PushTaskConfig config = config();
        config.setSql("${query!}");

        TaskResult result =
                runner(resources, publisher)
                        .run(
                                spec(config),
                                context(
                                        10L,
                                        new ExecutionHandle(10L),
                                        new RecordingLogService(),
                                        Map.of("query", "delete from source")));

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertTrue(result.getMessage().contains("read query"));
        assertEquals(0, resources.resolveCount.get());
        assertEquals(0, publisher.closeCount.get());
    }

    private PushTaskRunner runner(ResourceResolver resources, RecordingPublisher publisher) {
        MessageSerializerRegistry serializers =
                new MessageSerializerRegistry(
                        List.of(new JsonMessageSerializer(new ObjectMapper())));
        CompressionRegistry compressions =
                new CompressionRegistry(List.of(new GzipCompressionService()));
        PushPublisherResolver publisherResolver =
                new PushPublisherResolver(List.of(new RecordingPublisherFactory(publisher)));
        publisherResolver.init();
        return new PushTaskRunner(
                resources,
                publisherResolver,
                new PublisherChainBuilder(new RetryExecutor()),
                serializers,
                compressions,
                new PushTaskConfigValidator(),
                new PluginConfigCodec(new ObjectMapper()));
    }

    private PushTaskConfig config() {
        PushTaskConfig config = new PushTaskConfig();
        config.setSourceResourceId(11L);
        config.setSql("select id, name from source");
        PushTaskPublishConfig target = new PushTaskPublishConfig();
        target.setResourceId(12L);
        target.setType(ResourceType.KAFKA);
        target.setProperties(Map.of("topic", "jobs-ready"));
        config.setTarget(target);
        return config;
    }

    private TaskExecutionSpec spec(PushTaskConfig config) {
        return new TaskExecutionSpec(
                "push", PluginType.PUSH, JSONUtil.toJsonStr(config), 0, 0, 30, true, null);
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

    private static DataSource dataSource(List<List<Object>> rows) {
        return dataSource(rows, null);
    }

    private static DataSource dataSource(
            List<List<Object>> rows, AtomicReference<String> preparedSql) {
        ResultSetMetaData metadata =
                proxy(
                        ResultSetMetaData.class,
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "getColumnCount" -> 2;
                                    case "getColumnLabel" -> (Integer) args[0] == 1 ? "id" : "name";
                                    default -> defaultValue(method.getReturnType());
                                });
        AtomicInteger index = new AtomicInteger(-1);
        ResultSet resultSet =
                proxy(
                        ResultSet.class,
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "next" -> index.incrementAndGet() < rows.size();
                                    case "getMetaData" -> metadata;
                                    case "getObject" ->
                                            rows.get(index.get()).get((Integer) args[0] - 1);
                                    default -> defaultValue(method.getReturnType());
                                });
        PreparedStatement statement =
                proxy(
                        PreparedStatement.class,
                        (proxy, method, args) ->
                                "executeQuery".equals(method.getName())
                                        ? resultSet
                                        : defaultValue(method.getReturnType()));
        DatabaseMetaData databaseMetaData =
                proxy(
                        DatabaseMetaData.class,
                        (proxy, method, args) ->
                                "getDatabaseProductName".equals(method.getName())
                                        ? "PostgreSQL"
                                        : defaultValue(method.getReturnType()));
        Connection connection =
                proxy(
                        Connection.class,
                        (proxy, method, args) -> {
                            if ("prepareStatement".equals(method.getName())) {
                                if (preparedSql != null) {
                                    preparedSql.set((String) args[0]);
                                }
                                return statement;
                            }
                            if ("getMetaData".equals(method.getName())) {
                                return databaseMetaData;
                            }
                            return defaultValue(method.getReturnType());
                        });
        return proxy(
                DataSource.class,
                (proxy, method, args) ->
                        "getConnection".equals(method.getName())
                                ? connection
                                : defaultValue(method.getReturnType()));
    }

    private interface PublishAction {
        CompletableFuture<Void> publish(PushMessage message);
    }

    private static final class RecordingPublisher implements PushPublisher {
        private final List<PushMessage> messages = new ArrayList<>();
        private final AtomicInteger closeCount = new AtomicInteger();
        private final CountDownLatch started = new CountDownLatch(1);
        private PushTaskPublishConfig createdConfig;
        private PublishAction action = message -> CompletableFuture.completedFuture(null);

        @Override
        public CompletableFuture<Void> publish(PushMessage message) {
            messages.add(message);
            return action.publish(message);
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
        }
    }

    private record RecordingPublisherFactory(RecordingPublisher publisher)
            implements PushPublisherFactory {
        @Override
        public ResourceType supportType() {
            return ResourceType.KAFKA;
        }

        @Override
        public PushPublisher create(PushTaskPublishConfig config) {
            publisher.createdConfig = config;
            return publisher;
        }
    }

    private static final class RecordingResolver extends ResourceResolver {
        private final DataSource dataSource;
        private final AtomicInteger resolveCount = new AtomicInteger();

        private RecordingResolver(DataSource dataSource) {
            super(null);
            this.dataSource = dataSource;
        }

        @Override
        public DataSource getDataSource(Long resourceId) {
            resolveCount.incrementAndGet();
            return dataSource;
        }
    }

    private static final class RecordingLogService extends InstanceLogService {
        private String error = "";

        private RecordingLogService() {
            super(null);
        }

        @Override
        public void error(Long instanceId, String content) {
            error = content;
        }

        @Override
        public void info(Long instanceId, String content) {}
    }

    @Test
    void publishWindowTimeoutShouldReturnTimedOut() {
        RecordingPublisher publisher = new RecordingPublisher();
        publisher.action = message -> new CompletableFuture<>();
        PushTaskConfig config = config();
        config.setRetryTimes(0);
        config.setPublishTimeoutSeconds(1);

        TaskResult result =
                runner(new RecordingResolver(dataSource(List.of(List.of(1L, "first")))), publisher)
                        .run(
                                spec(config),
                                context(6L, new ExecutionHandle(6L), new RecordingLogService()));

        assertEquals(InstanceStatus.TIMED_OUT, result.getStatus());
        assertEquals("Push publish timeout", result.getMessage());
    }

    private static final class RecordingInstanceService extends InstanceService {
        private RecordingInstanceService() {
            super(null, null, null, null);
        }

        @Override
        public void updateProgress(Long instanceId, Integer progress) {}
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

    private record ObjectMethodHandler(InvocationHandler delegate) implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return switch (method.getName()) {
                case "toString" -> "PushTaskJdbcFixture";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> delegate.invoke(proxy, method, args);
            };
        }
    }
}
