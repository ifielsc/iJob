package com.ifelio.ijob.plugin.apicall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.core.execute.ExecutionHandle;
import com.ifelio.ijob.core.execute.task.CancellationReason;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.execute.variable.VariableResolver;
import com.ifelio.ijob.core.instance.InstanceLogService;
import com.ifelio.ijob.core.resource.ResourceClientLease;
import com.ifelio.ijob.core.resource.ResourceResolver;
import com.ifelio.ijob.core.resource.factory.service.client.HttpServiceClient;
import com.ifelio.ijob.core.resource.factory.service.client.ServiceClient;
import com.ifelio.ijob.core.task.definition.TaskExecutionSpec;
import com.ifelio.ijob.plugin.apicall.response.JsonPathResponseExtractorService;
import com.ifelio.ijob.plugin.apicall.response.JsonPathSuccessEvaluator;
import com.ifelio.ijob.plugin.common.config.PluginConfigCodec;
import com.sun.net.httpserver.HttpServer;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import okhttp3.OkHttpClient;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class ApiRemoteStopTest {
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final ApiRemoteStopDispatcher dispatcher =
            new ApiRemoteStopDispatcher(new SimpleMeterRegistry());
    private final CountDownLatch mainReceived = new CountDownLatch(1),
            releaseMain = new CountDownLatch(1),
            stopReceived = new CountDownLatch(1),
            releaseStop = new CountDownLatch(1),
            leaseReleased = new CountDownLatch(1);
    private final AtomicInteger stops = new AtomicInteger(), released = new AtomicInteger();
    private final AtomicReference<String> mainId = new AtomicReference<>(),
            stopId = new AtomicReference<>();
    private final List<String> logs = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private ServiceClient client;
    private volatile int stopStatus = 202, mainStatus = 200;
    private ExecutionHandle handle;
    private TaskContext context;
    private ApiCallTaskRunner runner;

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(workers);
        server.createContext(
                "/",
                exchange -> {
                    boolean stop =
                            exchange.getRequestURI().getPath().equals("/stop")
                                    || "urn:stop"
                                            .equals(
                                                    exchange.getRequestHeaders()
                                                            .getFirst("SOAPAction"));
                    String body =
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8);
                    if (stop) {
                        stops.incrementAndGet();
                        stopId.set(body);
                        stopReceived.countDown();
                    } else {
                        mainId.set(body);
                        mainReceived.countDown();
                    }
                    try {
                        (stop ? releaseStop : releaseMain).await(5, TimeUnit.SECONDS);
                        byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(stop ? stopStatus : mainStatus, bytes.length);
                        exchange.getResponseBody().write(bytes);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        exchange.close();
                    }
                });
        server.start();
        client =
                new HttpServiceClient(
                        new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
                        "http://127.0.0.1:" + server.getAddress().getPort());
        ResourceResolver resolver =
                new ResourceResolver(null) {
                    @Override
                    public ResourceClientLease<ServiceClient> leaseServiceClient(Long id) {
                        return new ResourceClientLease<>(
                                client,
                                () -> {
                                    released.incrementAndGet();
                                    leaseReleased.countDown();
                                });
                    }
                };
        runner =
                new ApiCallTaskRunner(
                        resolver,
                        new JsonPathResponseExtractorService(),
                        new JsonPathSuccessEvaluator(),
                        new ApiCallTaskConfigValidator(),
                        new PluginConfigCodec(new ObjectMapper()),
                        dispatcher);
        handle = new ExecutionHandle(17L);
        context =
                new TaskContext(
                        17L,
                        handle,
                        null,
                        new InstanceLogService(null) {
                            @Override
                            public void info(Long id, String text) {
                                logs.add(text);
                            }

                            @Override
                            public void error(Long id, String text) {
                                logs.add(text);
                            }
                        },
                        new VariableResolver());
    }

    @AfterEach
    void cleanup() {
        releaseMain.countDown();
        releaseStop.countDown();
        dispatcher.destroy();
        if (server != null) server.stop(0);
        if (client != null) client.close();
        workers.shutdownNow();
    }

    private TaskExecutionSpec spec(boolean enabled) throws Exception {
        ApiCallConfig config = new ApiCallConfig();
        config.setResourceId(1L);
        config.setPath("/work");
        config.setBody(Map.of("operationId", "${ijob.apiCall.operationId}"));
        if (enabled) {
            RemoteStopConfig stop = new RemoteStopConfig();
            stop.setPath("/stop");
            stop.setBody(Map.of("operationId", "${ijob.apiCall.operationId}"));
            config.setRemoteStop(stop);
        } else config.setBody(Map.of("operationId", "legacy"));
        return new TaskExecutionSpec(
                "stop",
                PluginType.API_CALL,
                new ObjectMapper().writeValueAsString(config),
                0,
                0,
                10,
                true,
                null);
    }

    @Test
    void stopIsOnceCorrelatedIndependentAndHoldsOriginalLease() throws Exception {
        var spec = spec(true);
        handle.bind(runner, spec, context);
        var future = workers.submit(() -> runner.run(spec, context));
        assertTrue(mainReceived.await(3, TimeUnit.SECONDS));
        handle.requestStop();
        handle.requestStop();
        handle.requestTimeout();
        assertEquals(InstanceStatus.STOPPED, future.get(3, TimeUnit.SECONDS).getStatus());
        assertTrue(stopReceived.await(3, TimeUnit.SECONDS));
        assertEquals(0, released.get(), "Stop still owns the original lease");
        assertEquals(mainId.get(), stopId.get());
        assertFalse(stopId.get().contains("${"));
        releaseStop.countDown();
        assertTrue(leaseReleased.await(3, TimeUnit.SECONDS));
        assertEquals(1, released.get());
        assertEquals(1, stops.get());
        assertTrue(logs.stream().anyMatch(s -> s.contains("outcome=ACCEPTED")));
        assertTrue(context.getOutputVariables().isEmpty());
    }

    @Test
    void successfulCloseDoesNotSendStop() throws Exception {
        releaseMain.countDown();
        var spec = spec(true);
        handle.bind(runner, spec, context);
        assertEquals(InstanceStatus.SUCCESS, runner.run(spec, context).getStatus());
        context.cleanupResources();
        handle.requestStop();
        assertEquals(0, stops.get());
        assertEquals(1, released.get());
    }

    @Test
    void disabledAndFencedCallsOnlyCancelLocally() throws Exception {
        var spec = spec(false);
        handle.bind(runner, spec, context);
        var future = workers.submit(() -> runner.run(spec, context));
        assertTrue(mainReceived.await(3, TimeUnit.SECONDS));
        handle.requestStop();
        assertEquals(InstanceStatus.STOPPED, future.get(3, TimeUnit.SECONDS).getStatus());
        assertEquals(0, stops.get());
    }

    @Test
    void fencingNeverSendsRemoteControl() throws Exception {
        var spec = spec(true);
        handle.bind(runner, spec, context);
        var future = workers.submit(() -> runner.run(spec, context));
        assertTrue(mainReceived.await(3, TimeUnit.SECONDS));
        handle.requestFence();
        assertEquals(InstanceStatus.STOPPED, future.get(3, TimeUnit.SECONDS).getStatus());
        assertEquals(0, stops.get());
    }

    @Test
    void cancellationBeforeDispatchNeverSendsBusinessOrStop() throws Exception {
        var spec = spec(true);
        handle.bind(runner, spec, context);
        handle.requestStop();
        assertEquals(InstanceStatus.STOPPED, runner.run(spec, context).getStatus());
        assertEquals(1, mainReceived.getCount());
        assertEquals(0, stops.get());
    }

    @Test
    void taskTimeoutNotificationPrecedesCleanupAndDoesNotBlockIt() throws Exception {
        var spec = spec(true);
        handle.bind(runner, spec, context);
        var future = workers.submit(() -> runner.run(spec, context));
        assertTrue(mainReceived.await(3, TimeUnit.SECONDS));
        context.cancel(CancellationReason.TIMEOUT);
        context.cleanupResources();
        future.get(3, TimeUnit.SECONDS);
        assertTrue(stopReceived.await(3, TimeUnit.SECONDS));
        assertEquals(1, stops.get());
    }

    @Test
    void remoteDeadlineReleasesLeaseWithoutRetry() throws Exception {
        var original = spec(true);
        var config = new ObjectMapper().readValue(original.getPluginConfig(), ApiCallConfig.class);
        config.getRemoteStop().setTimeoutSeconds(1);
        var spec =
                new TaskExecutionSpec(
                        "deadline",
                        PluginType.API_CALL,
                        new ObjectMapper().writeValueAsString(config),
                        0,
                        0,
                        10,
                        true,
                        null);
        handle.bind(runner, spec, context);
        var future = workers.submit(() -> runner.run(spec, context));
        assertTrue(mainReceived.await(3, TimeUnit.SECONDS));
        handle.requestStop();
        assertEquals(InstanceStatus.STOPPED, future.get(3, TimeUnit.SECONDS).getStatus());
        assertTrue(stopReceived.await(3, TimeUnit.SECONDS));
        assertTrue(leaseReleased.await(3, TimeUnit.SECONDS));
        assertEquals(1, stops.get());
        assertTrue(logs.stream().anyMatch(s -> s.contains("outcome=TIMED_OUT")));
    }

    @Test
    void remoteRejectionIsObservedWithoutChangingLocalStopOrRetrying() throws Exception {
        stopStatus = 503;
        releaseStop.countDown();
        var spec = spec(true);
        handle.bind(runner, spec, context);
        var future = workers.submit(() -> runner.run(spec, context));
        assertTrue(mainReceived.await(3, TimeUnit.SECONDS));
        handle.requestStop();
        assertEquals(InstanceStatus.STOPPED, future.get(3, TimeUnit.SECONDS).getStatus());
        assertTrue(leaseReleased.await(3, TimeUnit.SECONDS));
        assertEquals(1, stops.get());
        assertTrue(logs.stream().anyMatch(s -> s.contains("outcome=FAILED")));
    }

    @Test
    void businessFailureDoesNotSendStop() throws Exception {
        mainStatus = 500;
        releaseMain.countDown();
        var spec = spec(true);
        handle.bind(runner, spec, context);
        assertEquals(InstanceStatus.FAILED, runner.run(spec, context).getStatus());
        assertEquals(0, stops.get());
        assertEquals(1, released.get());
    }

    @Test
    void transportReadTimeoutSendsOneStop() throws Exception {
        client.close();
        client =
                new HttpServiceClient(
                        new OkHttpClient.Builder().readTimeout(300, TimeUnit.MILLISECONDS).build(),
                        "http://127.0.0.1:" + server.getAddress().getPort());
        releaseStop.countDown();
        var spec = spec(true);
        handle.bind(runner, spec, context);
        var result = runner.run(spec, context);
        assertEquals(InstanceStatus.TIMED_OUT, result.getStatus());
        assertTrue(stopReceived.await(3, TimeUnit.SECONDS));
        assertTrue(leaseReleased.await(3, TimeUnit.SECONDS));
        assertEquals(1, stops.get());
    }

    @Test
    void soapStopUsesIndependentOperationOnSameService() throws Exception {
        client.close();
        var resource = new com.ifelio.ijob.core.resource.factory.service.ServiceResourceConfig();
        resource.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/service");
        var soap = new com.ifelio.ijob.core.resource.factory.service.WebServiceConfig();
        soap.setNamespace("urn:test");
        resource.setWebService(soap);
        client =
                new com.ifelio.ijob.core.resource.factory.service.client.WebServiceClient(
                        new OkHttpClient(), resource);
        var config = new ApiCallConfig();
        config.setResourceId(1L);
        var main = new WebServiceCallConfig();
        main.setOperation("work");
        main.setAction("urn:work");
        main.setParameters(Map.of("id", "${ijob.apiCall.operationId}"));
        config.setWebService(main);
        var stop = new RemoteStopConfig();
        var control = new WebServiceCallConfig();
        control.setOperation("stop");
        control.setAction("urn:stop");
        control.setParameters(Map.of("id", "${ijob.apiCall.operationId}"));
        stop.setWebService(control);
        config.setRemoteStop(stop);
        var spec =
                new TaskExecutionSpec(
                        "soap",
                        PluginType.API_CALL,
                        new ObjectMapper().writeValueAsString(config),
                        0,
                        0,
                        10,
                        true,
                        null);
        handle.bind(runner, spec, context);
        releaseStop.countDown();
        stopStatus = 200;
        var future = workers.submit(() -> runner.run(spec, context));
        assertTrue(mainReceived.await(3, TimeUnit.SECONDS));
        handle.requestStop();
        assertEquals(InstanceStatus.STOPPED, future.get(3, TimeUnit.SECONDS).getStatus());
        assertTrue(stopReceived.await(3, TimeUnit.SECONDS));
        assertTrue(leaseReleased.await(3, TimeUnit.SECONDS));
        assertTrue(mainId.get().contains(":work>"));
        assertTrue(stopId.get().contains(":stop>"));
        String id = mainId.get().split("<id>")[1].split("</id>")[0];
        assertTrue(stopId.get().contains("<id>" + id + "</id>"));
        assertFalse(id.contains("${"));
    }

    @Test
    void runtimeFenceDropsQueuedControlAndRejectsNewControl() throws Exception {
        CountDownLatch started = new CountDownLatch(2), unblock = new CountDownLatch(1);
        for (int i = 0; i < 2; i++)
            dispatcher.submit(
                    () -> {
                        started.countDown();
                        try {
                            unblock.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    },
                    () -> {});
        assertTrue(started.await(3, TimeUnit.SECONDS));
        var spec = spec(true);
        handle.bind(runner, spec, context);
        var future = workers.submit(() -> runner.run(spec, context));
        assertTrue(mainReceived.await(3, TimeUnit.SECONDS));
        handle.requestStop();
        future.get(3, TimeUnit.SECONDS);
        dispatcher.onRuntimeFenced("test");
        unblock.countDown();
        assertTrue(leaseReleased.await(3, TimeUnit.SECONDS));
        assertEquals(0, stops.get());
        assertFalse(dispatcher.submit(() -> {}, () -> {}));
    }

    @Test
    void saturatedDispatcherRejectsWithoutBlockingAndReleasesOwnership() throws Exception {
        CountDownLatch blockers = new CountDownLatch(2), unblock = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();
        for (int i = 0; i < 2; i++)
            assertTrue(
                    dispatcher.submit(
                            () -> {
                                blockers.countDown();
                                try {
                                    unblock.await();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            },
                            completed::incrementAndGet));
        assertTrue(blockers.await(3, TimeUnit.SECONDS));
        for (int i = 0; i < 32; i++)
            assertTrue(dispatcher.submit(() -> {}, completed::incrementAndGet));
        assertFalse(
                dispatcher.submit(
                        () -> {
                            throw new AssertionError();
                        },
                        completed::incrementAndGet));
        assertEquals(1, completed.get());
        dispatcher.destroy();
        unblock.countDown();
    }
}
