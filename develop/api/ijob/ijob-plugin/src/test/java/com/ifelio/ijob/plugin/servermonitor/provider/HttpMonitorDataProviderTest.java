package com.ifelio.ijob.plugin.servermonitor.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.exception.StopException;
import com.ifelio.ijob.contract.monitor.AgentInfo;
import com.ifelio.ijob.contract.monitor.MonitorCollectRequest;
import com.ifelio.ijob.contract.monitor.MonitorReport;
import com.ifelio.ijob.core.execute.ExecutionHandle;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.resource.ResourceClientLease;
import com.ifelio.ijob.core.resource.ResourceResolver;
import com.ifelio.ijob.core.resource.factory.monitor.client.MonitorClient;
import com.ifelio.ijob.plugin.support.RecordingInstanceLogs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class HttpMonitorDataProviderTest {
    private ExecutorService executor;

    @AfterEach
    void closeExecutor() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldCollectThroughDedicatedLeaseAndReleaseIt() {
        CapturingMonitorClient client = new CapturingMonitorClient();
        CapturingResourceResolver resolver = new CapturingResourceResolver(client, null);
        HttpMonitorDataProvider provider = new HttpMonitorDataProvider(resolver);
        MonitorCollectRequest request = MonitorCollectRequest.builder().build();
        TaskContext context = context(new ExecutionHandle(1L));

        MonitorReport report = provider.collect(12L, request, context);

        assertEquals(12L, resolver.resourceId);
        assertSame(request, client.request);
        assertSame(client.report, report);
        assertEquals(1, client.closeCount.get());
    }

    @Test
    void stopDuringLeaseResolutionShouldReleaseWithoutCollecting() {
        ExecutionHandle handle = new ExecutionHandle(2L);
        CapturingMonitorClient client = new CapturingMonitorClient();
        CapturingResourceResolver resolver = new CapturingResourceResolver(client, handle);
        HttpMonitorDataProvider provider = new HttpMonitorDataProvider(resolver);

        assertThrows(
                StopException.class,
                () ->
                        provider.collect(
                                13L, MonitorCollectRequest.builder().build(), context(handle)));

        assertEquals(0, client.collectCount.get());
        assertEquals(1, client.closeCount.get());
    }

    @Test
    void cleanupShouldCloseLeaseAndUnblockActiveCollection() throws Exception {
        BlockingMonitorClient client = new BlockingMonitorClient();
        CapturingResourceResolver resolver = new CapturingResourceResolver(client, null);
        HttpMonitorDataProvider provider = new HttpMonitorDataProvider(resolver);
        TaskContext context = context(new ExecutionHandle(3L));
        executor = Executors.newSingleThreadExecutor();
        Future<MonitorReport> future =
                executor.submit(
                        () ->
                                provider.collect(
                                        14L, MonitorCollectRequest.builder().build(), context));
        assertTrue(client.entered.await(2, TimeUnit.SECONDS));

        context.cleanupResources();

        assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
        assertEquals(1, client.closeCount.get());
    }

    @Test
    void pauseBeforeCollectionShouldDelayLeaseAndClientCall() throws Exception {
        ExecutionHandle handle = new ExecutionHandle(4L);
        handle.requestPause();
        CapturingMonitorClient client = new CapturingMonitorClient();
        CapturingResourceResolver resolver = new CapturingResourceResolver(client, null);
        HttpMonitorDataProvider provider = new HttpMonitorDataProvider(resolver);
        executor = Executors.newSingleThreadExecutor();

        Future<MonitorReport> future =
                executor.submit(
                        () ->
                                provider.collect(
                                        15L,
                                        MonitorCollectRequest.builder().build(),
                                        context(handle)));

        assertThrows(
                java.util.concurrent.TimeoutException.class,
                () -> future.get(200, TimeUnit.MILLISECONDS));
        assertEquals(0, resolver.leaseCount.get());
        assertEquals(0, client.collectCount.get());
        handle.resume();
        future.get(2, TimeUnit.SECONDS);
        assertEquals(1, resolver.leaseCount.get());
        assertEquals(1, client.collectCount.get());
    }

    private TaskContext context(ExecutionHandle handle) {
        return new TaskContext(1L, handle, null, new RecordingInstanceLogs(), null);
    }

    private static class CapturingResourceResolver extends ResourceResolver {
        private final MonitorClient client;
        private final ExecutionHandle stopDuringLease;
        private Long resourceId;
        private final AtomicInteger leaseCount = new AtomicInteger();

        CapturingResourceResolver(MonitorClient client, ExecutionHandle stopDuringLease) {
            super(null);
            this.client = client;
            this.stopDuringLease = stopDuringLease;
        }

        @Override
        public ResourceClientLease<MonitorClient> leaseMonitorClient(Long resourceId) {
            leaseCount.incrementAndGet();
            this.resourceId = resourceId;
            if (stopDuringLease != null) {
                stopDuringLease.requestStop();
            }
            return new ResourceClientLease<>(client, client::close);
        }
    }

    private static class CapturingMonitorClient implements MonitorClient {
        private final MonitorReport report = new MonitorReport();
        private final AtomicInteger collectCount = new AtomicInteger();
        final AtomicInteger closeCount = new AtomicInteger();
        private MonitorCollectRequest request;

        @Override
        public boolean ping() {
            return true;
        }

        @Override
        public AgentInfo getAgentInfo() {
            return null;
        }

        @Override
        public MonitorReport collect(MonitorCollectRequest request) {
            this.request = request;
            collectCount.incrementAndGet();
            return report;
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
        }
    }

    private static final class BlockingMonitorClient extends CapturingMonitorClient {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch closed = new CountDownLatch(1);

        @Override
        public MonitorReport collect(MonitorCollectRequest request) {
            entered.countDown();
            try {
                closed.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", e);
            }
            throw new IllegalStateException("closed");
        }

        @Override
        public void close() {
            super.close();
            closed.countDown();
        }
    }
}
