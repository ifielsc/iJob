package com.ifielsc.ijob.plugin.servermonitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.exception.StopException;
import com.ifielsc.ijob.contract.monitor.AgentInfo;
import com.ifielsc.ijob.contract.monitor.CollectType;
import com.ifielsc.ijob.contract.monitor.MonitorCollectRequest;
import com.ifielsc.ijob.contract.monitor.MonitorReport;
import com.ifielsc.ijob.contract.monitor.OSType;
import com.ifielsc.ijob.contract.monitor.metric.CpuMetric;
import com.ifielsc.ijob.core.execute.ExecutionHandle;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.plugin.servermonitor.analyzer.CompositeMonitorAnalyzer;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorCollectConfig;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorConfig;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorRuleConfig;
import com.ifielsc.ijob.plugin.servermonitor.handler.CompositeMonitorHandler;
import com.ifielsc.ijob.plugin.servermonitor.handler.MonitorHandler;
import com.ifielsc.ijob.plugin.servermonitor.provider.MonitorDataProvider;
import com.ifielsc.ijob.plugin.support.RecordingInstanceLogs;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class MonitorPipelineTest {
    @Test
    void shouldPassConfiguredResourceAndCollectItemsToProvider() {
        CapturingMonitorDataProvider provider = new CapturingMonitorDataProvider(successReport());
        RecordingHandler handler = new RecordingHandler();
        MonitorPipeline pipeline = pipeline(provider, handler);
        MonitorConfig config = baseConfig();
        TaskContext context = context(new ExecutionHandle(1L));

        MonitorCheckResult result = pipeline.execute(config, context);

        assertEquals(21L, provider.resourceId);
        assertNotNull(provider.request);
        assertEquals(List.of(CollectType.CPU), provider.request.getCollectItems());
        assertEquals(1, handler.calls.get());
        assertNotNull(result);
        List<String> logs = ((RecordingInstanceLogs) context.getInstanceLogService()).messages();
        assertTrue(logs.stream().anyMatch(line -> line.contains("Monitor collection started")));
        assertTrue(logs.stream().anyMatch(line -> line.contains("report validated")));
        assertTrue(logs.stream().anyMatch(line -> line.contains("Monitor analysis completed")));
        assertTrue(
                logs.stream().anyMatch(line -> line.contains("Monitor result handlers completed")));
        assertEquals(
                List.of(5, 10, 60, 70, 85, 95),
                ((RecordingInstanceService) context.getInstanceService()).progressValues);
    }

    @Test
    void unsuccessfulAgentReportShouldFailBeforeHandling() {
        MonitorReport failed =
                MonitorReport.builder().success(false).message("secret remote detail").build();
        CapturingMonitorDataProvider provider = new CapturingMonitorDataProvider(failed);
        RecordingHandler handler = new RecordingHandler();

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                pipeline(provider, handler)
                                        .execute(baseConfig(), context(new ExecutionHandle(2L))));

        assertEquals("monitor agent collection was unsuccessful", exception.getMessage());
        assertEquals(0, handler.calls.get());
    }

    @Test
    void stopAfterCollectionShouldPreventAnalysisAndHandling() {
        ExecutionHandle handle = new ExecutionHandle(3L);
        CapturingMonitorDataProvider provider = new CapturingMonitorDataProvider(successReport());
        provider.stopAfterCollect = handle;
        RecordingHandler handler = new RecordingHandler();

        assertThrows(
                StopException.class,
                () -> pipeline(provider, handler).execute(baseConfig(), context(handle)));

        assertEquals(0, handler.calls.get());
    }

    @Test
    void missingCollectConfigShouldFailExplicitly() {
        MonitorConfig config = baseConfig();
        config.setCollect(null);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                pipeline(
                                                new CapturingMonitorDataProvider(successReport()),
                                                new RecordingHandler())
                                        .execute(config, context(new ExecutionHandle(4L))));

        assertEquals("monitor collect config is required", exception.getMessage());
    }

    @Test
    void pauseDuringSlowCollectionShouldTakeEffectAtPostCollectCheckpoint() throws Exception {
        ExecutionHandle handle = new ExecutionHandle(5L);
        BlockingMonitorDataProvider provider = new BlockingMonitorDataProvider(successReport());
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<MonitorCheckResult> future =
                    executor.submit(
                            () ->
                                    pipeline(provider, new RecordingHandler())
                                            .execute(baseConfig(), context(handle)));
            assertTrue(provider.entered.await(2, TimeUnit.SECONDS));

            handle.requestPause();
            provider.release.countDown();

            assertThrows(
                    java.util.concurrent.TimeoutException.class,
                    () -> future.get(200, TimeUnit.MILLISECONDS));
            handle.resume();
            assertNotNull(future.get(2, TimeUnit.SECONDS));
        }
    }

    private MonitorPipeline pipeline(MonitorDataProvider provider, MonitorHandler handler) {
        return new MonitorPipeline(
                provider,
                new CompositeMonitorAnalyzer(List.of()),
                new CompositeMonitorHandler(List.of(handler)),
                new MonitorReportValidator());
    }

    private MonitorConfig baseConfig() {
        return MonitorConfig.builder()
                .resourceId(21L)
                .collect(MonitorCollectConfig.builder().items(List.of(CollectType.CPU)).build())
                .rules(MonitorRuleConfig.builder().build())
                .build();
    }

    private MonitorReport successReport() {
        return MonitorReport.builder()
                .success(true)
                .agent(
                        AgentInfo.builder()
                                .version("1.0.0")
                                .hostname("test-host")
                                .osType(OSType.LINUX)
                                .startTime(LocalDateTime.now())
                                .build())
                .collectTime(LocalDateTime.now(ZoneOffset.UTC))
                .elapsed(10L)
                .cpu(CpuMetric.builder().usage(10D).build())
                .build();
    }

    private TaskContext context(ExecutionHandle handle) {
        return new TaskContext(
                1L, handle, new RecordingInstanceService(), new RecordingInstanceLogs(), null);
    }

    private static final class RecordingInstanceService extends InstanceService {
        private final List<Integer> progressValues = new java.util.ArrayList<>();

        private RecordingInstanceService() {
            super(null, null, null, null);
        }

        @Override
        public void updateProgress(Long instanceId, Integer progress) {
            progressValues.add(progress);
        }
    }

    private static class CapturingMonitorDataProvider implements MonitorDataProvider {
        private final MonitorReport report;
        private Long resourceId;
        private MonitorCollectRequest request;
        private ExecutionHandle stopAfterCollect;

        CapturingMonitorDataProvider(MonitorReport report) {
            this.report = report;
        }

        @Override
        public MonitorReport collect(
                Long resourceId, MonitorCollectRequest request, TaskContext context) {
            this.resourceId = resourceId;
            this.request = request;
            if (stopAfterCollect != null) {
                stopAfterCollect.requestStop();
            }
            return report;
        }
    }

    private static final class BlockingMonitorDataProvider extends CapturingMonitorDataProvider {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private BlockingMonitorDataProvider(MonitorReport report) {
            super(report);
        }

        @Override
        public MonitorReport collect(
                Long resourceId, MonitorCollectRequest request, TaskContext context) {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return super.collect(resourceId, request, context);
        }
    }

    private static final class RecordingHandler implements MonitorHandler {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public boolean support(MonitorConfig config) {
            return true;
        }

        @Override
        public void handle(MonitorCheckResult result, MonitorConfig config, TaskContext context) {
            calls.incrementAndGet();
        }
    }
}
