package com.ifielsc.ijob.plugin.datacheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.exception.StopException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.core.execute.ExecutionHandle;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.task.TaskResult;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.instance.InstanceLogService;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;
import com.ifielsc.ijob.plugin.common.config.PluginConfigCodec;
import com.ifielsc.ijob.plugin.datacheck.config.CheckLevel;
import com.ifielsc.ijob.plugin.datacheck.config.CheckType;
import com.ifielsc.ijob.plugin.datacheck.config.CompareType;
import com.ifielsc.ijob.plugin.datacheck.config.DataCheckConfig;
import com.ifielsc.ijob.plugin.datacheck.config.DataCheckItemConfig;
import com.ifielsc.ijob.plugin.datacheck.model.DataCheckContext;
import com.ifielsc.ijob.plugin.datacheck.model.DataCheckResult;

import org.junit.jupiter.api.Test;

import java.sql.SQLSyntaxErrorException;
import java.sql.SQLTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class DataCheckTaskRunnerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void successfulCheckShouldReturnSuccess() throws Exception {
        StubPipeline pipeline = new StubPipeline();
        pipeline.result = DataCheckResult.from(List.of(), true);

        TaskResult result =
                runner(pipeline).run(spec(objectMapper.writeValueAsString(config())), context());

        assertEquals(InstanceStatus.SUCCESS, result.getStatus());
        assertEquals(1, pipeline.calls.get());
    }

    @Test
    void errorCheckShouldReturnFailure() throws Exception {
        StubPipeline pipeline = new StubPipeline();
        pipeline.result = DataCheckResult.from(List.of(), false);

        TaskResult result =
                runner(pipeline).run(spec(objectMapper.writeValueAsString(config())), context());

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertEquals("data check failed", result.getMessage());
    }

    @Test
    void invalidConfigShouldFailBeforePipeline() {
        StubPipeline pipeline = new StubPipeline();

        TaskResult result = runner(pipeline).run(spec("{}"), context());

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertEquals(0, pipeline.calls.get());
        assertTrue(result.getMessage().contains("resourceId"));
    }

    @Test
    void runtimeArgumentFailureShouldNotExposeSensitiveMessage() throws Exception {
        StubPipeline pipeline = new StubPipeline();
        pipeline.failure = new IllegalArgumentException("actual=secret-value");
        RecordingLogService logs = new RecordingLogService();

        TaskResult result =
                runner(pipeline)
                        .run(spec(objectMapper.writeValueAsString(config())), context(logs));

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertEquals("data check failed", result.getMessage());
        assertFalse(logs.error.contains("secret-value"));
        assertTrue(logs.error.contains("IllegalArgumentException"));
    }

    @Test
    void queryTimeoutShouldReturnTimedOutWithoutSensitiveDetails() throws Exception {
        StubPipeline pipeline = new StubPipeline();
        pipeline.failure =
                new IllegalStateException("query failed", new SQLTimeoutException("jdbc details"));

        TaskResult result =
                runner(pipeline).run(spec(objectMapper.writeValueAsString(config())), context());

        assertEquals(InstanceStatus.TIMED_OUT, result.getStatus());
        assertEquals("Data check query timeout", result.getMessage());
    }

    @Test
    void permanentResourceFailureShouldNotRetry() throws Exception {
        StubPipeline pipeline = new StubPipeline();
        pipeline.failure = new BusinessException(ResultCode.NOT_FOUND, "resource details");

        TaskResult result =
                runner(pipeline).run(spec(objectMapper.writeValueAsString(config())), context());

        assertFalse(result.isRetryableFailure());
        assertEquals("data check resource is invalid", result.getMessage());
    }

    @Test
    void transientResourceFailureShouldRetry() throws Exception {
        StubPipeline pipeline = new StubPipeline();
        pipeline.failure =
                new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "temporary details");

        TaskResult result =
                runner(pipeline).run(spec(objectMapper.writeValueAsString(config())), context());

        assertTrue(result.isRetryableFailure());
        assertEquals("data check failed", result.getMessage());
    }

    @Test
    void nonTransientSqlFailureShouldNotRetry() throws Exception {
        StubPipeline pipeline = new StubPipeline();
        pipeline.failure =
                new IllegalStateException("query details", new SQLSyntaxErrorException("syntax"));

        TaskResult result =
                runner(pipeline).run(spec(objectMapper.writeValueAsString(config())), context());

        assertFalse(result.isRetryableFailure());
        assertEquals("data check statement or access is invalid", result.getMessage());
    }

    @Test
    void stopShouldMapStatusAndCleanupRegisteredResources() throws Exception {
        StubPipeline pipeline = new StubPipeline();
        pipeline.failure = new StopException();
        DataCheckTaskRunner runner = runner(pipeline);
        TaskContext context = context();
        AtomicInteger closed = new AtomicInteger();
        context.registerResource(closed::incrementAndGet);

        TaskResult result = runner.run(spec(objectMapper.writeValueAsString(config())), context);
        runner.stop(spec("{}"), context);

        assertEquals(InstanceStatus.STOPPED, result.getStatus());
        assertEquals(1, closed.get());
    }

    @Test
    void sqlAndComparisonValuesShouldResolveBeforeValidation() throws Exception {
        StubPipeline pipeline = new StubPipeline();
        pipeline.result = DataCheckResult.from(List.of(), true);
        DataCheckConfig config = config();
        DataCheckItemConfig item = config.getChecks().getFirst();
        item.setSql("select value from metrics where tenant_id=${tenantId!}");
        item.setExpected("${expected!}");

        TaskResult result =
                runner(pipeline)
                        .run(
                                spec(objectMapper.writeValueAsString(config)),
                                context(Map.of("tenantId", 9, "expected", 12L)));

        assertEquals(InstanceStatus.SUCCESS, result.getStatus());
        assertEquals(
                "select value from metrics where tenant_id=9",
                pipeline.lastConfig.getChecks().getFirst().getSql());
        assertEquals(12L, pipeline.lastConfig.getChecks().getFirst().getExpected());
    }

    @Test
    void resolvedWriteQueryAndMissingVariableShouldFailBeforePipeline() throws Exception {
        StubPipeline pipeline = new StubPipeline();
        pipeline.result = DataCheckResult.from(List.of(), true);
        DataCheckConfig config = config();
        config.getChecks().getFirst().setSql("${query!}");

        TaskResult writeResult =
                runner(pipeline)
                        .run(
                                spec(objectMapper.writeValueAsString(config)),
                                context(Map.of("query", "delete from metrics")));
        TaskResult missingResult =
                runner(pipeline).run(spec(objectMapper.writeValueAsString(config)), context());

        assertEquals(InstanceStatus.FAILED, writeResult.getStatus());
        assertTrue(writeResult.getMessage().contains("read query"));
        assertEquals("required variable missing:query", missingResult.getMessage());
        assertEquals(0, pipeline.calls.get());
    }

    private DataCheckTaskRunner runner(StubPipeline pipeline) {
        return new DataCheckTaskRunner(
                new PluginConfigCodec(objectMapper), pipeline, new DataCheckConfigValidator());
    }

    private DataCheckConfig config() {
        DataCheckItemConfig item = new DataCheckItemConfig();
        item.setName("count-check");
        item.setSql("select id from ifj_task");
        item.setType(CheckType.COUNT);
        item.setCompareType(CompareType.GE);
        item.setExpected(1);
        item.setLevel(CheckLevel.ERROR);
        DataCheckConfig config = new DataCheckConfig();
        config.setResourceId(7L);
        config.setChecks(List.of(item));
        return config;
    }

    private TaskExecutionSpec spec(String pluginConfig) {
        return new TaskExecutionSpec(
                "data-check", PluginType.DATA_CHECK, pluginConfig, 0, 0, 30, true, null);
    }

    private TaskContext context() {
        return context(new RecordingLogService(), Map.of());
    }

    private TaskContext context(Map<String, Object> variables) {
        return context(new RecordingLogService(), variables);
    }

    private TaskContext context(InstanceLogService logs) {
        return context(logs, Map.of());
    }

    private TaskContext context(InstanceLogService logs, Map<String, Object> variables) {
        ExecutionHandle handle = new ExecutionHandle(61L);
        return new TaskContext(61L, handle, null, logs, new VariableResolver(), variables);
    }

    private static final class StubPipeline extends DataCheckPipeline {
        private final AtomicInteger calls = new AtomicInteger();
        private DataCheckResult result;
        private RuntimeException failure;
        private DataCheckConfig lastConfig;

        private StubPipeline() {
            super(null, null, null);
        }

        @Override
        public DataCheckResult execute(DataCheckConfig config, DataCheckContext context) {
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
