package com.ifelio.ijob.plugin.script;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.common.exception.StopException;
import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.core.execute.ExecutionHandle;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.execute.task.TaskResult;
import com.ifelio.ijob.core.execute.variable.VariableResolver;
import com.ifelio.ijob.core.task.definition.TaskExecutionSpec;
import com.ifelio.ijob.plugin.common.config.PluginConfigCodec;
import com.ifelio.ijob.plugin.script.executor.RemoteSshScriptExecutor;
import com.ifelio.ijob.plugin.support.RecordingInstanceLogs;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeoutException;

class ScriptTaskRunnerTest {
    @Test
    void lifecycleLogsDoNotIncludeScriptOutput() {
        TaskContext context = context();
        runner(result(0, "secret stdout", "secret stderr")).run(spec(validConfig()), context);
        List<String> logs = ((RecordingInstanceLogs) context.getInstanceLogService()).messages();
        org.junit.jupiter.api.Assertions.assertTrue(
                logs.stream().anyMatch(line -> line.contains("Script execution started")));
        org.junit.jupiter.api.Assertions.assertTrue(
                logs.stream().anyMatch(line -> line.contains("exitCode=0")));
        org.junit.jupiter.api.Assertions.assertFalse(logs.toString().contains("secret"));
    }

    @Test
    void shouldMapZeroAndNonZeroExitCodes() {
        TaskResult success = runner(result(0, "", "")).run(spec(validConfig()), context());
        TaskResult failure = runner(result(7, "", "failed")).run(spec(validConfig()), context());

        assertEquals(InstanceStatus.SUCCESS, success.getStatus());
        assertEquals(InstanceStatus.FAILED, failure.getStatus());
        assertEquals("Script exited with a non-zero status", failure.getMessage());
    }

    @Test
    void shouldPreserveTimeoutAndStopTerminalMeaning() {
        TaskResult timeout =
                runnerThrowing(new TimeoutException("timeout")).run(spec(validConfig()), context());
        TaskResult stopped =
                runnerThrowing(new StopException()).run(spec(validConfig()), context());

        assertEquals(InstanceStatus.TIMED_OUT, timeout.getStatus());
        assertEquals("Script execution timed out", timeout.getMessage());
        assertEquals(InstanceStatus.STOPPED, stopped.getStatus());
    }

    @Test
    void outputCollectionDelayShouldFailWithoutClaimingExecutionTimeout() {
        TaskResult result =
                runnerThrowing(
                                new ScriptOutputCollectionException(
                                        "Script output collection did not finish"))
                        .run(spec(validConfig()), context());

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertEquals("Script execution failed", result.getMessage());
    }

    @Test
    void nonZeroFailureMessageShouldNotExposeStderr() {
        TaskResult result =
                runner(result(1, "", "错".repeat(20_000))).run(spec(validConfig()), context());

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertEquals("Script exited with a non-zero status", result.getMessage());
    }

    @Test
    void invalidConfigurationShouldFailBeforeExecutorRuns() {
        RecordingExecutor executor = new RecordingExecutor(result(0, "", ""), null);
        ScriptTaskRunner runner = runner(executor);

        TaskResult result = runner.run(spec("{\"engine\":\"BAT\",\"script\":\"\"}"), context());

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertEquals("Script content is required", result.getMessage());
        assertEquals(0, executor.executionCount);
    }

    @Test
    void sshModeShouldUseRemoteExecutorWithoutCallingLocalRegistry() {
        RecordingExecutor local = new RecordingExecutor(result(1, "", "local"), null);
        RecordingRemoteExecutor remote = new RecordingRemoteExecutor(result(0, "remote", ""));
        ScriptTaskRunner runner =
                new ScriptTaskRunner(
                        new ScriptExecutorRegistry(List.of(local)),
                        new ScriptTaskConfigValidator(),
                        new PluginConfigCodec(new ObjectMapper()),
                        remote);

        TaskResult taskResult =
                runner.run(
                        spec(
                                "{\"executionMode\":\"SSH\",\"sshResourceId\":7,"
                                        + "\"engine\":\"SHELL\","
                                        + "\"script\":\"echo accepted\"}"),
                        context());

        assertEquals(InstanceStatus.SUCCESS, taskResult.getStatus());
        assertEquals(0, local.executionCount);
        assertEquals(1, remote.executionCount);
    }

    private ScriptTaskRunner runner(ScriptResult result) {
        return runner(new RecordingExecutor(result, null));
    }

    private ScriptTaskRunner runnerThrowing(Exception exception) {
        return runner(new RecordingExecutor(null, exception));
    }

    private ScriptTaskRunner runner(RecordingExecutor executor) {
        return new ScriptTaskRunner(
                new ScriptExecutorRegistry(List.of(executor)),
                new ScriptTaskConfigValidator(),
                new PluginConfigCodec(new ObjectMapper()),
                null);
    }

    private ScriptResult result(int exitCode, String stdout, String stderr) {
        return ScriptResult.builder().exitCode(exitCode).stdout(stdout).stderr(stderr).build();
    }

    private String validConfig() {
        return "{\"engine\":\"BAT\",\"script\":\"echo accepted\"}";
    }

    private TaskExecutionSpec spec(String config) {
        return new TaskExecutionSpec("script", PluginType.SCRIPT, config, 0, 0, 5, true, null);
    }

    private TaskContext context() {
        return new TaskContext(
                1L,
                new ExecutionHandle(1L),
                null,
                new RecordingInstanceLogs(),
                new VariableResolver());
    }

    private static final class RecordingExecutor implements ScriptExecutor {
        private final ScriptResult result;
        private final Exception exception;
        private int executionCount;

        private RecordingExecutor(ScriptResult result, Exception exception) {
            this.result = result;
            this.exception = exception;
        }

        @Override
        public ScriptEngine support() {
            return ScriptEngine.BAT;
        }

        @Override
        public ScriptResult execute(
                ScriptTaskConfig config, TaskExecutionSpec spec, TaskContext context)
                throws Exception {
            executionCount++;
            if (exception != null) {
                throw exception;
            }
            return result;
        }
    }

    private static final class RecordingRemoteExecutor extends RemoteSshScriptExecutor {
        private final ScriptResult result;
        private int executionCount;

        private RecordingRemoteExecutor(ScriptResult result) {
            super(null, null, null);
            this.result = result;
        }

        @Override
        public ScriptResult execute(
                ScriptTaskConfig config, TaskExecutionSpec spec, TaskContext context) {
            executionCount++;
            return result;
        }
    }
}
