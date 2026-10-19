// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.servermonitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.json.JSONUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.exception.StopException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.contract.monitor.CollectType;
import com.ifielsc.ijob.contract.monitor.application.ApplicationCollectConfig;
import com.ifielsc.ijob.contract.monitor.application.ProcessMatchType;
import com.ifielsc.ijob.core.execute.ExecutionHandle;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.task.TaskResult;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.instance.InstanceLogService;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;
import com.ifielsc.ijob.plugin.common.config.PluginConfigCodec;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorCollectConfig;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorConfig;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorRuleConfig;
import com.ifielsc.ijob.plugin.servermonitor.rule.ApplicationMonitorRule;
import com.ifielsc.ijob.plugin.servermonitor.rule.CpuMonitorRule;
import com.ifielsc.ijob.plugin.servermonitor.rule.DiskMonitorRule;
import com.ifielsc.ijob.plugin.servermonitor.rule.Threshold;

import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class ServerMonitorTaskRunnerTest {
    @Test
    void validConfigWithoutAlarmShouldSucceed() {
        StubPipeline pipeline = new StubPipeline();
        ServerMonitorTaskRunner runner = runner(pipeline);

        TaskResult result = runner.run(spec(JSONUtil.toJsonStr(baseConfig())), context());

        assertEquals(InstanceStatus.SUCCESS, result.getStatus());
        assertEquals(1, pipeline.calls.get());
    }

    @Test
    void alarmShouldSucceedAfterHandlersProcessHealthResult() {
        StubPipeline pipeline = new StubPipeline();
        pipeline.result.add(MonitorCheckItem.builder().level(AlarmLevel.WARNING).build());

        TaskResult result = runner(pipeline).run(spec(JSONUtil.toJsonStr(baseConfig())), context());

        assertEquals(InstanceStatus.SUCCESS, result.getStatus());
    }

    @Test
    void invalidConfigShouldFailBeforePipeline() {
        StubPipeline pipeline = new StubPipeline();

        TaskResult result = runner(pipeline).run(spec("{}"), context());

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertEquals(0, pipeline.calls.get());
    }

    @Test
    void providerFailureShouldBeGenericAndSensitiveSafe() {
        StubPipeline pipeline = new StubPipeline();
        pipeline.failure = new IllegalStateException("token=secret-value");
        RecordingLogService logs = new RecordingLogService();

        TaskResult result =
                runner(pipeline).run(spec(JSONUtil.toJsonStr(baseConfig())), context(logs));

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertEquals("server monitor failed", result.getMessage());
        assertFalse(logs.error.contains("secret-value"));
        assertTrue(logs.error.contains("IllegalStateException"));
        assertTrue(result.isRetryableFailure());
    }

    @Test
    void permanentResourceFailureShouldNotRetry() {
        StubPipeline pipeline = new StubPipeline();
        pipeline.failure = new BusinessException(ResultCode.NOT_FOUND, "resource details");

        TaskResult result = runner(pipeline).run(spec(JSONUtil.toJsonStr(baseConfig())), context());

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertFalse(result.isRetryableFailure());
        assertEquals("server monitor resource is invalid", result.getMessage());
    }

    @Test
    void transientResourceFailureShouldRetry() {
        StubPipeline pipeline = new StubPipeline();
        pipeline.failure =
                new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "temporary details");

        TaskResult result = runner(pipeline).run(spec(JSONUtil.toJsonStr(baseConfig())), context());

        assertTrue(result.isRetryableFailure());
        assertEquals("server monitor failed", result.getMessage());
    }

    @Test
    void runtimeApplicationValuesShouldResolveBeforeValidation() {
        StubPipeline pipeline = new StubPipeline();
        MonitorConfig config =
                MonitorConfig.builder()
                        .resourceId(12L)
                        .collect(
                                MonitorCollectConfig.builder()
                                        .items(List.of(CollectType.APPLICATION))
                                        .applications(
                                                List.of(
                                                        ApplicationCollectConfig.builder()
                                                                .name("${application!}")
                                                                .matchType(ProcessMatchType.PID)
                                                                .matchValue("${pid!}")
                                                                .required(true)
                                                                .allowMultiple(false)
                                                                .build()))
                                        .build())
                        .rules(
                                MonitorRuleConfig.builder()
                                        .applications(
                                                List.of(
                                                        ApplicationMonitorRule.builder()
                                                                .name("${application!}")
                                                                .required(true)
                                                                .build()))
                                        .build())
                        .build();

        TaskResult result =
                runner(pipeline)
                        .run(
                                spec(JSONUtil.toJsonStr(config)),
                                context(Map.of("application", "worker", "pid", 1234)));

        assertEquals(InstanceStatus.SUCCESS, result.getStatus());
        assertEquals(
                "worker", pipeline.lastConfig.getCollect().getApplications().getFirst().getName());
        assertEquals(
                "1234",
                pipeline.lastConfig.getCollect().getApplications().getFirst().getMatchValue());
        assertEquals(
                "worker", pipeline.lastConfig.getRules().getApplications().getFirst().getName());
    }

    @Test
    void invalidResolvedProcessMatchShouldFailBeforePipeline() {
        StubPipeline pipeline = new StubPipeline();
        MonitorConfig config =
                MonitorConfig.builder()
                        .resourceId(12L)
                        .collect(
                                MonitorCollectConfig.builder()
                                        .items(List.of(CollectType.APPLICATION))
                                        .applications(
                                                List.of(
                                                        ApplicationCollectConfig.builder()
                                                                .name("worker")
                                                                .matchType(ProcessMatchType.PID)
                                                                .matchValue("${pid!}")
                                                                .required(true)
                                                                .allowMultiple(false)
                                                                .build()))
                                        .build())
                        .rules(
                                MonitorRuleConfig.builder()
                                        .applications(
                                                List.of(
                                                        ApplicationMonitorRule.builder()
                                                                .name("worker")
                                                                .required(true)
                                                                .build()))
                                        .build())
                        .build();

        TaskResult result =
                runner(pipeline)
                        .run(spec(JSONUtil.toJsonStr(config)), context(Map.of("pid", "not-a-pid")));

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertEquals("application PID is invalid", result.getMessage());
        assertEquals(0, pipeline.calls.get());
    }

    @Test
    void runtimeDiskPathShouldResolveAndNormalizeBeforeExecution() {
        StubPipeline pipeline = new StubPipeline();
        MonitorConfig config = diskConfig(Map.of("${mount!}", threshold(80D, 90D)));

        TaskResult result =
                runner(pipeline)
                        .run(
                                spec(JSONUtil.toJsonStr(config)),
                                context(Map.of("mount", "D:\\data\\")));

        assertEquals(InstanceStatus.SUCCESS, result.getStatus());
        assertTrue(pipeline.lastConfig.getRules().getDisk().getPaths().containsKey("D:/data"));
    }

    @Test
    void duplicateResolvedDiskPathsShouldFailBeforePipeline() {
        StubPipeline pipeline = new StubPipeline();
        MonitorConfig config =
                diskConfig(
                        Map.of(
                                "${first!}", threshold(80D, 90D),
                                "${second!}", threshold(70D, 85D)));

        TaskResult result =
                runner(pipeline)
                        .run(
                                spec(JSONUtil.toJsonStr(config)),
                                context(Map.of("first", "/data", "second", "/data")));

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertEquals("resolved monitor disk paths must be unique", result.getMessage());
        assertEquals(0, pipeline.calls.get());
    }

    @Test
    void providerSocketTimeoutShouldMapToTimedOut() {
        StubPipeline pipeline = new StubPipeline();
        pipeline.failure = new RuntimeException(new SocketTimeoutException("read timed out"));

        TaskResult result = runner(pipeline).run(spec(JSONUtil.toJsonStr(baseConfig())), context());

        assertEquals(InstanceStatus.TIMED_OUT, result.getStatus());
        assertEquals("server monitor timeout", result.getMessage());
    }

    @Test
    void stopShouldMapStatusAndCleanupRegisteredResources() {
        StubPipeline pipeline = new StubPipeline();
        pipeline.failure = new StopException();
        ServerMonitorTaskRunner runner = runner(pipeline);
        TaskContext context = context();
        AtomicInteger closed = new AtomicInteger();
        context.registerResource(closed::incrementAndGet);

        TaskResult result = runner.run(spec(JSONUtil.toJsonStr(baseConfig())), context);

        assertEquals(1, closed.get());
        runner.stop(spec("{}"), context);

        assertEquals(InstanceStatus.STOPPED, result.getStatus());
        assertEquals(1, closed.get());
    }

    private ServerMonitorTaskRunner runner(StubPipeline pipeline) {
        return new ServerMonitorTaskRunner(
                pipeline,
                new ServerMonitorTaskConfigValidator(),
                new PluginConfigCodec(new ObjectMapper()));
    }

    private MonitorConfig baseConfig() {
        return MonitorConfig.builder()
                .resourceId(12L)
                .collect(MonitorCollectConfig.builder().items(List.of(CollectType.CPU)).build())
                .rules(
                        MonitorRuleConfig.builder()
                                .cpu(
                                        CpuMonitorRule.builder()
                                                .usage(
                                                        Threshold.builder()
                                                                .warning(70D)
                                                                .critical(90D)
                                                                .build())
                                                .build())
                                .build())
                .build();
    }

    private MonitorConfig diskConfig(Map<String, Threshold> paths) {
        return MonitorConfig.builder()
                .resourceId(12L)
                .collect(MonitorCollectConfig.builder().items(List.of(CollectType.DISK)).build())
                .rules(
                        MonitorRuleConfig.builder()
                                .disk(
                                        DiskMonitorRule.builder()
                                                .usage(threshold(75D, 90D))
                                                .paths(paths)
                                                .build())
                                .build())
                .build();
    }

    private Threshold threshold(double warning, double critical) {
        return Threshold.builder().warning(warning).critical(critical).build();
    }

    private TaskExecutionSpec spec(String config) {
        return new TaskExecutionSpec(
                "server-monitor", PluginType.SERVER_MONITOR, config, 0, 0, 30, true, null);
    }

    private TaskContext context() {
        return context(new RecordingLogService());
    }

    private TaskContext context(Map<String, Object> variables) {
        return new TaskContext(
                1L,
                new ExecutionHandle(1L),
                null,
                new RecordingLogService(),
                new VariableResolver(),
                variables);
    }

    private TaskContext context(InstanceLogService logs) {
        return new TaskContext(1L, new ExecutionHandle(1L), null, logs, new VariableResolver());
    }

    private static final class StubPipeline extends MonitorPipeline {
        private final AtomicInteger calls = new AtomicInteger();
        private final MonitorCheckResult result = new MonitorCheckResult();
        private MonitorConfig lastConfig;
        private RuntimeException failure;

        private StubPipeline() {
            super(null, null, null, null);
        }

        @Override
        public MonitorCheckResult execute(MonitorConfig config, TaskContext context) {
            calls.incrementAndGet();
            lastConfig = config;
            if (failure != null) {
                throw failure;
            }
            return result;
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
    }
}
