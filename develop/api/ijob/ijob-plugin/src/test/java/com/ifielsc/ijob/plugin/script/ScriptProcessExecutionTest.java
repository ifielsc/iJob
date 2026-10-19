package com.ifielsc.ijob.plugin.script;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.json.JSONUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.core.execute.ExecutionHandle;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.task.TaskResult;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.instance.InstanceLogService;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;
import com.ifielsc.ijob.plugin.common.config.PluginConfigCodec;
import com.ifielsc.ijob.plugin.script.executor.AbstractProcessScriptExecutor;
import com.ifielsc.ijob.plugin.script.executor.BatScriptExecutor;
import com.ifielsc.ijob.plugin.script.executor.PowerShellExecutor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class ScriptProcessExecutionTest {
    private final List<ExecutorService> executors = new ArrayList<>();

    @AfterEach
    void shutdownExecutors() {
        executors.forEach(ExecutorService::shutdownNow);
    }

    @Test
    void controlledScriptShouldCaptureOutputVariableAndDeleteTempFile() throws Exception {
        TestPlatformExecutor executor = executor(streamPool());
        RecordingLogService logs = new RecordingLogService();
        TaskContext context = context(new ExecutionHandle(1L), logs);

        ScriptResult result = executor.execute(config(successScript()), spec(5), context);

        assertAll(
                () -> assertEquals(0, result.getExitCode()),
                () -> assertTrue(result.getStdout().contains("accepted")),
                () -> assertEquals("42", context.getVariable("answer")),
                () ->
                        assertTrue(
                                logs.messages.stream()
                                        .anyMatch(message -> message.contains("accepted"))),
                () -> assertFalse(Files.exists(executor.scriptFile.toPath())),
                () -> assertFalse(executor.process.isAlive()));
    }

    @Test
    void nonZeroExitShouldReturnSafeTaskFailure() throws Exception {
        TestPlatformExecutor executor = executor(streamPool());
        ScriptTaskRunner runner = runner(executor);

        TaskResult result =
                runner.run(
                        spec(5, failureScript()),
                        context(new ExecutionHandle(2L), new RecordingLogService()));

        assertAll(
                () -> assertEquals(InstanceStatus.FAILED, result.getStatus()),
                () -> assertEquals("Script exited with a non-zero status", result.getMessage()),
                () -> assertFalse(Files.exists(executor.scriptFile.toPath())),
                () -> assertFalse(executor.process.isAlive()));
    }

    @RepeatedTest(20)
    void coreInterruptionShouldKillProcessAndDeleteTempFile() throws Exception {
        TestPlatformExecutor executor = executor(streamPool());
        TaskContext context = context(new ExecutionHandle(3L), new RecordingLogService());
        ExecutorService taskPool = register(Executors.newSingleThreadExecutor());
        CountDownLatch executionFinished = new CountDownLatch(1);
        AtomicReference<Exception> interruption = new AtomicReference<>();

        Future<ScriptResult> execution =
                taskPool.submit(
                        () -> {
                            try {
                                return executor.execute(
                                        config(longRunningScript()), spec(1), context);
                            } catch (Exception failure) {
                                interruption.set(failure);
                                throw failure;
                            } finally {
                                executionFinished.countDown();
                            }
                        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (executor.process == null && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertTrue(executor.process != null);
        assertTrue(execution.cancel(true));

        assertTrue(executionFinished.await(5, TimeUnit.SECONDS));
        awaitExit(executor.process.toHandle());
        assertTrue(
                interruption.get() instanceof InterruptedException,
                () -> String.valueOf(interruption.get()));
        assertAll(
                () -> assertFalse(executor.process.isAlive()),
                () -> assertFalse(Files.exists(executor.scriptFile.toPath())));
    }

    @Test
    void stopShouldKillProcessTreeAndReturnStopped() throws Exception {
        TestPlatformExecutor executor = executor(streamPool());
        ScriptTaskRunner runner = runner(executor);
        ExecutionHandle handle = new ExecutionHandle(4L);
        RecordingLogService logs = new RecordingLogService();
        TaskContext context = context(handle, logs);
        TaskExecutionSpec spec = spec(30, longRunningScript());
        handle.bind(runner, spec, context);
        ExecutorService taskExecutor = register(Executors.newSingleThreadExecutor());

        Future<TaskResult> future = taskExecutor.submit(() -> runner.run(spec, context));
        assertTrue(logs.ready.await(5, TimeUnit.SECONDS));
        List<ProcessHandle> descendants = executor.process.toHandle().descendants().toList();
        assertFalse(descendants.isEmpty());

        handle.requestStop();
        TaskResult result = future.get(5, TimeUnit.SECONDS);
        awaitExit(executor.process.toHandle());
        for (ProcessHandle descendant : descendants) {
            awaitExit(descendant);
        }

        assertAll(
                () -> assertEquals(InstanceStatus.STOPPED, result.getStatus()),
                () -> assertFalse(executor.process.isAlive()),
                () -> assertTrue(descendants.stream().noneMatch(ProcessHandle::isAlive)),
                () -> assertFalse(Files.exists(executor.scriptFile.toPath())));
    }

    @Test
    void saturatedStreamPoolShouldRejectAndReleaseStartedProcess() throws Exception {
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolExecutor saturated =
                new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new SynchronousQueue<>());
        register(saturated);
        saturated.execute(
                () -> {
                    occupied.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });
        assertTrue(occupied.await(2, TimeUnit.SECONDS));
        TestPlatformExecutor executor = executor(saturated);

        try {
            assertThrows(
                    RejectedExecutionException.class,
                    () ->
                            executor.execute(
                                    config(longRunningScript()),
                                    spec(30),
                                    context(new ExecutionHandle(5L), new RecordingLogService())));
            awaitExit(executor.process.toHandle());
            assertAll(
                    () -> assertFalse(executor.process.isAlive()),
                    () -> assertFalse(Files.exists(executor.scriptFile.toPath())),
                    () ->
                            assertEquals(
                                    0,
                                    new AppProperties()
                                            .getExecution()
                                            .getScript()
                                            .getQueueCapacity()));
        } finally {
            release.countDown();
        }
    }

    @Test
    void scriptRenderingShouldResolveIjobValuesAndPreserveEscapedNativeValues() throws Exception {
        TestPlatformExecutor executor = executor(streamPool());
        TaskContext context = context(new ExecutionHandle(6L), new RecordingLogService());
        context.putVariable("HOME", "ijob-home");
        ScriptTaskConfig config =
                config("echo ${HOME}\n" + "echo $${HOME}\n" + "echo $${HOME:-fallback}\n");
        config.setScript(context.resolveScript(config.getScript()));

        File rendered = executor.render(config, context, Charset.forName("UTF-8"));
        try {
            assertEquals(
                    "echo ijob-home\n" + "echo ${HOME}\n" + "echo ${HOME:-fallback}\n",
                    Files.readString(rendered.toPath(), Charset.forName("UTF-8")));
        } finally {
            Files.deleteIfExists(rendered.toPath());
        }
    }

    @Test
    void windowsBatShouldSupportExplicitUtf8ScriptAndOutputCharsets() throws Exception {
        if (!isWindows()) {
            return;
        }
        ScriptTaskConfig config = new ScriptTaskConfig();
        config.setEngine(ScriptEngine.BAT);
        config.setScript("@echo off\r\nchcp 65001 >nul\r\necho 中文编码\r\n");
        config.setScriptCharset("UTF-8");
        config.setOutputCharset("UTF-8");
        BatScriptExecutor executor =
                new BatScriptExecutor(streamPool(), new ProcessStreamCollector());

        ScriptResult result =
                executor.execute(
                        config,
                        spec(5),
                        context(new ExecutionHandle(7L), new RecordingLogService()));

        assertEquals(0, result.getExitCode());
        assertTrue(result.getStdout().contains("中文编码"));
    }

    @Test
    void windowsPowerShellShouldSupportSeparateScriptAndOutputCharsets() throws Exception {
        if (!isWindows()) {
            return;
        }
        ScriptTaskConfig config = new ScriptTaskConfig();
        config.setEngine(ScriptEngine.POWERSHELL);
        config.setScript(
                "[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false); "
                        + "Write-Output '中文编码'");
        config.setScriptCharset("UTF-16");
        config.setOutputCharset("UTF-8");
        PowerShellExecutor executor =
                new PowerShellExecutor(streamPool(), new ProcessStreamCollector());

        ScriptResult result =
                executor.execute(
                        config,
                        spec(5),
                        context(new ExecutionHandle(8L), new RecordingLogService()));

        assertEquals(0, result.getExitCode());
        assertTrue(result.getStdout().contains("中文编码"));
    }

    private TestPlatformExecutor executor(ExecutorService streamPool) {
        return new TestPlatformExecutor(streamPool, new ProcessStreamCollector());
    }

    private ScriptTaskRunner runner(TestPlatformExecutor executor) {
        return new ScriptTaskRunner(
                new ScriptExecutorRegistry(List.of(executor)),
                new ScriptTaskConfigValidator(),
                new PluginConfigCodec(new ObjectMapper()),
                null);
    }

    private ExecutorService streamPool() {
        return register(Executors.newFixedThreadPool(2));
    }

    private <T extends ExecutorService> T register(T executor) {
        executors.add(executor);
        return executor;
    }

    private TaskContext context(ExecutionHandle handle, InstanceLogService logs) {
        return new TaskContext(handle.getInstanceId(), handle, null, logs, new VariableResolver());
    }

    private ScriptTaskConfig config(String script) {
        ScriptTaskConfig config = new ScriptTaskConfig();
        config.setEngine(isWindows() ? ScriptEngine.BAT : ScriptEngine.SHELL);
        config.setScript(script);
        config.setCharset("UTF-8");
        return config;
    }

    private TaskExecutionSpec spec(int timeout) {
        return spec(timeout, successScript());
    }

    private TaskExecutionSpec spec(int timeout, String script) {
        return new TaskExecutionSpec(
                "script",
                PluginType.SCRIPT,
                JSONUtil.toJsonStr(config(script)),
                0,
                0,
                timeout,
                true,
                null);
    }

    private String successScript() {
        if (isWindows()) {
            return "@echo off\r\n" + "echo accepted\r\n" + "echo __VAR__:answer=42\r\n";
        }
        return "printf 'accepted\\n'\n" + "printf '__VAR__:answer=42\\n'\n";
    }

    private String failureScript() {
        if (isWindows()) {
            return "@echo off\r\n" + "echo rejected 1>&2\r\n" + "exit /b 7\r\n";
        }
        return "printf 'rejected\\n' >&2\nexit 7\n";
    }

    private String longRunningScript() {
        if (isWindows()) {
            return "@echo off\r\n"
                    + "echo READY\r\n"
                    + "powershell -NoProfile -NonInteractive -Command "
                    + "\"Start-Sleep -Seconds 30\"\r\n";
        }
        return "printf 'READY\\n'\nsleep 30\n";
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    private void awaitExit(ProcessHandle processHandle) throws Exception {
        if (processHandle.isAlive()) {
            processHandle.onExit().get(2, TimeUnit.SECONDS);
        }
        assertFalse(processHandle.isAlive());
    }

    private static final class RecordingLogService extends InstanceLogService {
        private final List<String> messages = new ArrayList<>();
        private final CountDownLatch ready = new CountDownLatch(1);

        private RecordingLogService() {
            super(null);
        }

        @Override
        public void info(Long instanceId, String content) {
            messages.add(content);
            if (content.contains("READY")) {
                ready.countDown();
            }
        }
    }

    private static final class TestPlatformExecutor extends AbstractProcessScriptExecutor {
        private volatile File scriptFile;
        private volatile Process process;

        private TestPlatformExecutor(
                ExecutorService scriptExecutorPool, ProcessStreamCollector streamCollector) {
            super(scriptExecutorPool, streamCollector);
        }

        private File render(ScriptTaskConfig config, TaskContext context, Charset charset)
                throws IOException {
            return createScriptFile(config, context, charset);
        }

        @Override
        public ScriptEngine support() {
            return System.getProperty("os.name").toLowerCase().contains("windows")
                    ? ScriptEngine.BAT
                    : ScriptEngine.SHELL;
        }

        @Override
        protected File createScriptFile(
                ScriptTaskConfig config, TaskContext context, Charset charset) throws IOException {
            scriptFile = super.createScriptFile(config, context, charset);
            return scriptFile;
        }

        @Override
        protected List<String> buildCommand(ScriptTaskConfig config, File file) {
            if (support() == ScriptEngine.BAT) {
                return List.of("cmd", "/c", file.getAbsolutePath());
            }
            return List.of("/bin/sh", file.getAbsolutePath());
        }

        @Override
        protected Process startProcess(ProcessBuilder builder) throws IOException {
            process = super.startProcess(builder);
            return process;
        }
    }
}
