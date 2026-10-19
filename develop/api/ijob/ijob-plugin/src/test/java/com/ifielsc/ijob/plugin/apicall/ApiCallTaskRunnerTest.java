// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.apicall;

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
import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.core.resource.factory.service.ServiceRequest;
import com.ifielsc.ijob.core.resource.factory.service.ServiceResponse;
import com.ifielsc.ijob.core.resource.factory.service.client.ServiceClient;
import com.ifielsc.ijob.core.resource.factory.service.client.ServiceInvocation;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;
import com.ifielsc.ijob.plugin.apicall.response.JsonPathResponseExtractorService;
import com.ifielsc.ijob.plugin.apicall.response.JsonPathSuccessEvaluator;
import com.ifielsc.ijob.plugin.common.config.PluginConfigCodec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class ApiCallTaskRunnerTest {
    private final List<ExecutorService> executors = new ArrayList<>();

    @AfterEach
    void cleanup() {
        executors.forEach(ExecutorService::shutdownNow);
    }

    @Test
    void successShouldResolveRequestEvaluateAndOnlyStoreExtractedValues() {
        RecordingClient client =
                new RecordingClient(
                        response(200, "{\"code\":\"OK\",\"data\":{\"token\":\"abc\"}}"));
        RecordingResolver resolver = new RecordingResolver(client);
        ApiCallTaskRunner runner = runner(resolver);
        ApiCallConfig config = base();
        config.setMethod("POST");
        config.setPath("/users/${id!}");
        config.setHeaders(Map.of("Authorization", "Bearer ${token!}"));
        config.setBody(Map.of("id", "${id!}"));
        config.setSuccessCondition(condition("$.code", "EQ", "OK"));
        config.setExtractors(List.of(extractor("accessToken", "$.data.token")));
        TaskContext context = context(1L, new ExecutionHandle(1L));
        context.putVariable("id", 7L);
        context.putVariable("token", "input-token");

        TaskResult result = runner.run(spec(config), context);

        ServiceRequest request = client.request;
        assertAll(
                () -> assertEquals(InstanceStatus.SUCCESS, result.getStatus()),
                () -> assertEquals(1, resolver.resolveCount),
                () -> assertEquals("/users/7", request.getPath()),
                () -> assertEquals("Bearer input-token", request.getHeaders().get("Authorization")),
                () -> assertEquals(7L, ((Map<?, ?>) request.getBody()).get("id")),
                () -> assertEquals("abc", context.getVariable("accessToken")),
                () -> assertFalse(context.containsVariable("response")),
                () ->
                        assertEquals(
                                ApiCallConfig.DEFAULT_MAX_RESPONSE_BYTES,
                                request.getMaxResponseBytes()),
                () -> assertEquals(1, client.closeCount.get()));
    }

    @Test
    void resolvedHeaderInjectionShouldFailBeforeResolvingResource() {
        RecordingResolver resolver =
                new RecordingResolver(new RecordingClient(response(200, "{}")));
        ApiCallConfig config = base();
        config.setHeaders(Map.of("X-Token", "${token!}"));
        TaskContext context = context(8L, new ExecutionHandle(8L));
        context.putVariable("token", "safe\r\nInjected: true");

        TaskResult result = runner(resolver).run(spec(config), context);

        assertAll(
                () -> assertEquals(InstanceStatus.FAILED, result.getStatus()),
                () -> assertEquals(0, resolver.resolveCount));
    }

    @Test
    void transportAndBusinessFailuresShouldNotPublishResponseVariables() {
        RecordingClient transportClient = new RecordingClient(response(503, "{\"code\":\"OK\"}"));
        transportClient.httpStatusSuccess = true;
        TaskContext transportContext = context(2L, new ExecutionHandle(2L));
        TaskResult transport =
                runner(new RecordingResolver(transportClient)).run(spec(base()), transportContext);

        RecordingClient businessClient =
                new RecordingClient(response(200, "{\"code\":\"FAILED\"}"));
        ApiCallConfig businessConfig = base();
        businessConfig.setSuccessCondition(condition("$.code", "EQ", "OK"));
        TaskContext businessContext = context(3L, new ExecutionHandle(3L));
        TaskResult business =
                runner(new RecordingResolver(businessClient))
                        .run(spec(businessConfig), businessContext);

        assertAll(
                () -> assertEquals(InstanceStatus.FAILED, transport.getStatus()),
                () -> assertEquals("api invoke failed status:503", transport.getMessage()),
                () -> assertFalse(transportContext.containsVariable("response")),
                () -> assertEquals(InstanceStatus.FAILED, business.getStatus()),
                () -> assertEquals("business response failed", business.getMessage()),
                () -> assertFalse(businessContext.containsVariable("response")));
    }

    @Test
    void extractionFailureShouldNotPublishFullResponse() {
        RecordingClient client = new RecordingClient(response(200, "{\"data\":{\"id\":7}}"));
        ApiCallConfig config = base();
        config.setExtractors(
                List.of(extractor("id", "$.data.id"), extractor("missing", "$.data.missing")));
        TaskContext context = context(4L, new ExecutionHandle(4L));

        TaskResult result = runner(new RecordingResolver(client)).run(spec(config), context);

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertFalse(context.containsVariable("id"));
        assertFalse(context.containsVariable("response"));
    }

    @Test
    void runtimeFailureShouldNotExposeTransportDetails() {
        RecordingClient client = new RecordingClient(response(200, "{}"));
        client.failure = new IllegalStateException("secret endpoint and credential detail");

        TaskResult result =
                runner(new RecordingResolver(client))
                        .run(spec(base()), context(9L, new ExecutionHandle(9L)));

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertEquals("API call failed", result.getMessage());
    }

    @Test
    void invalidConfigurationShouldFailBeforeResolvingResource() {
        RecordingResolver resolver =
                new RecordingResolver(new RecordingClient(response(200, "{}")));
        ApiCallConfig config = base();
        config.setMethod("TRACE");

        TaskResult result =
                runner(resolver).run(spec(config), context(5L, new ExecutionHandle(5L)));

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertEquals(0, resolver.resolveCount);
    }

    @Test
    void stopShouldCancelBlockedInvocationAndReturnStopped() throws Exception {
        BlockingClient client = new BlockingClient();
        ApiCallTaskRunner runner = runner(new RecordingResolver(client));
        ApiCallConfig config = base();
        TaskExecutionSpec spec = spec(config);
        ExecutionHandle handle = new ExecutionHandle(6L);
        TaskContext context = context(6L, handle);
        handle.bind(runner, spec, context);
        ExecutorService executor = register(Executors.newSingleThreadExecutor());

        Future<TaskResult> future = executor.submit(() -> runner.run(spec, context));
        assertTrue(client.started.await(2, TimeUnit.SECONDS));
        handle.requestStop();
        TaskResult result = future.get(2, TimeUnit.SECONDS);

        assertAll(
                () -> assertEquals(InstanceStatus.STOPPED, result.getStatus()),
                () -> assertTrue(client.cancelled.get()),
                () -> assertEquals(1, client.closeCount.get()));
    }

    @Test
    void stopDuringResourceResolutionShouldNotCreateInvocation() throws Exception {
        RecordingClient client = new RecordingClient(response(200, "{}"));
        CountDownLatch resolving = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ResourceResolver resolver =
                new ResourceResolver(null) {
                    @Override
                    public com.ifielsc.ijob.core.resource.ResourceClientLease<ServiceClient>
                            leaseServiceClient(Long resourceId) {
                        resolving.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                        return new com.ifielsc.ijob.core.resource.ResourceClientLease<>(
                                client, () -> {});
                    }
                };
        ApiCallTaskRunner runner = runner(resolver);
        ApiCallConfig config = base();
        TaskExecutionSpec spec = spec(config);
        ExecutionHandle handle = new ExecutionHandle(7L);
        TaskContext context = context(7L, handle);
        handle.bind(runner, spec, context);
        ExecutorService executor = register(Executors.newSingleThreadExecutor());

        Future<TaskResult> future = executor.submit(() -> runner.run(spec, context));
        assertTrue(resolving.await(2, TimeUnit.SECONDS));
        handle.requestStop();
        release.countDown();
        TaskResult result = future.get(2, TimeUnit.SECONDS);

        assertAll(
                () -> assertEquals(InstanceStatus.STOPPED, result.getStatus()),
                () -> assertEquals(0, client.closeCount.get()),
                () -> assertTrue(client.request == null));
    }

    private ApiCallTaskRunner runner(ResourceResolver resolver) {
        return new ApiCallTaskRunner(
                resolver,
                new JsonPathResponseExtractorService(),
                new JsonPathSuccessEvaluator(),
                new ApiCallTaskConfigValidator(),
                new PluginConfigCodec(new ObjectMapper()),
                new ApiRemoteStopDispatcher(
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
    }

    private ApiCallConfig base() {
        ApiCallConfig config = new ApiCallConfig();
        config.setResourceId(12L);
        config.setMethod("GET");
        return config;
    }

    private TaskExecutionSpec spec(ApiCallConfig config) {
        return new TaskExecutionSpec(
                "api-call", PluginType.API_CALL, JSONUtil.toJsonStr(config), 0, 0, 30, true, null);
    }

    private TaskContext context(long id, ExecutionHandle handle) {
        return new TaskContext(id, handle, null, new RecordingLogService(), new VariableResolver());
    }

    private ServiceResponse response(int status, String body) {
        ServiceResponse response = new ServiceResponse();
        response.setStatus(status);
        response.setBody(body);
        return response;
    }

    private SuccessCondition condition(String expression, String operator, String expected) {
        SuccessCondition condition = new SuccessCondition();
        condition.setExpression(expression);
        condition.setOperator(operator);
        condition.setExpected(expected);
        return condition;
    }

    private ResponseExtractor extractor(String variable, String expression) {
        ResponseExtractor extractor = new ResponseExtractor();
        extractor.setVariable(variable);
        extractor.setExpression(expression);
        return extractor;
    }

    private <T extends ExecutorService> T register(T executor) {
        executors.add(executor);
        return executor;
    }

    private static final class RecordingResolver extends ResourceResolver {
        private final ServiceClient client;
        private int resolveCount;

        private RecordingResolver(ServiceClient client) {
            super(null);
            this.client = client;
        }

        @Override
        public com.ifielsc.ijob.core.resource.ResourceClientLease<ServiceClient> leaseServiceClient(
                Long resourceId) {
            resolveCount++;
            assertEquals(12L, resourceId);
            return new com.ifielsc.ijob.core.resource.ResourceClientLease<>(client, () -> {});
        }
    }

    private static final class RecordingClient implements ServiceClient {
        private final ServiceResponse response;
        private final AtomicInteger closeCount = new AtomicInteger();
        private volatile ServiceRequest request;
        private boolean httpStatusSuccess;
        private RuntimeException failure;

        private RecordingClient(ServiceResponse response) {
            this.response = response;
        }

        @Override
        public ServiceResponse invoke(ServiceRequest request) {
            this.request = request;
            return response;
        }

        @Override
        public ServiceInvocation newInvocation(ServiceRequest request) {
            this.request = request;
            return new ServiceInvocation() {
                private final AtomicBoolean closed = new AtomicBoolean();

                @Override
                public ServiceResponse execute() {
                    if (failure != null) {
                        throw failure;
                    }
                    return response;
                }

                @Override
                public void close() {
                    if (closed.compareAndSet(false, true)) {
                        closeCount.incrementAndGet();
                    }
                }
            };
        }

        @Override
        public boolean success(ServiceResponse value) {
            return !httpStatusSuccess || value.getStatus() >= 200 && value.getStatus() < 300;
        }
    }

    private static final class BlockingClient implements ServiceClient {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicInteger closeCount = new AtomicInteger();

        @Override
        public ServiceResponse invoke(ServiceRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServiceInvocation newInvocation(ServiceRequest request) {
            return new ServiceInvocation() {
                @Override
                public ServiceResponse execute() {
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("interrupted", e);
                    }
                    if (cancelled.get()) {
                        throw new IllegalStateException("cancelled");
                    }
                    return new ServiceResponse();
                }

                @Override
                public void close() {
                    if (cancelled.compareAndSet(false, true)) {
                        closeCount.incrementAndGet();
                        release.countDown();
                    }
                }
            };
        }
    }

    private static final class RecordingLogService extends InstanceLogService {
        private RecordingLogService() {
            super(null);
        }

        @Override
        public void info(Long instanceId, String content) {}

        @Override
        public void error(Long instanceId, String content) {}
    }
}
