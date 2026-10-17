package com.ifelio.ijob.plugin.script.executor;

import cn.hutool.core.map.MapUtil;

import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.task.definition.TaskExecutionSpec;
import com.ifelio.ijob.plugin.script.ProcessResource;
import com.ifelio.ijob.plugin.script.ProcessStreamCollector;
import com.ifelio.ijob.plugin.script.ScriptExecutor;
import com.ifelio.ijob.plugin.script.ScriptOutputAwaiter;
import com.ifelio.ijob.plugin.script.ScriptResult;
import com.ifelio.ijob.plugin.script.ScriptTaskConfig;

import io.micrometer.common.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public abstract class AbstractProcessScriptExecutor implements ScriptExecutor {
    private final ExecutorService scriptExecutorPool;
    private final ProcessStreamCollector streamCollector;

    protected AbstractProcessScriptExecutor(
            ExecutorService scriptExecutorPool, ProcessStreamCollector streamCollector) {
        this.scriptExecutorPool = scriptExecutorPool;
        this.streamCollector = streamCollector;
    }

    protected File createScriptFile(ScriptTaskConfig config, TaskContext context, Charset charset)
            throws IOException {
        File file = File.createTempFile("task_", support().getSuffix());
        Files.writeString(file.toPath(), config.getScript(), charset);
        return file;
    }

    protected abstract List<String> buildCommand(ScriptTaskConfig config, File scriptFile);

    @Override
    public ScriptResult execute(
            ScriptTaskConfig config, TaskExecutionSpec spec, TaskContext context) throws Exception {
        Charset scriptCharset = resolveCharset(config.getScriptCharset(), config.getCharset());
        Charset outputCharset = resolveCharset(config.getOutputCharset(), config.getCharset());
        File scriptFile = createScriptFile(config, context, scriptCharset);
        Future<String> stdoutFuture = null;
        Future<String> stderrFuture = null;

        try {
            ProcessBuilder builder = new ProcessBuilder(buildCommand(config, scriptFile));
            if (MapUtil.isNotEmpty(config.getEnv())) {
                builder.environment().putAll(config.getEnv());
            }

            if (StringUtils.isNotBlank(config.getWorkingDir())) {
                builder.directory(new File(config.getWorkingDir()));
            }

            context.checkStop();
            Process process = startProcess(builder);
            context.registerResource(new ProcessResource(process));
            context.checkStop();

            stdoutFuture =
                    streamCollector.collect(
                            context,
                            process.getInputStream(),
                            "STDOUT",
                            outputCharset,
                            scriptExecutorPool);
            stderrFuture =
                    streamCollector.collect(
                            context,
                            process.getErrorStream(),
                            "STDERR",
                            outputCharset,
                            scriptExecutorPool);

            waitWithoutTimeout(process);
            context.checkStop();

            String stdout = ScriptOutputAwaiter.await(stdoutFuture, 5, TimeUnit.SECONDS);
            String stderr = ScriptOutputAwaiter.await(stderrFuture, 5, TimeUnit.SECONDS);
            return ScriptResult.builder()
                    .exitCode(process.exitValue())
                    .stdout(stdout)
                    .stderr(stderr)
                    .build();
        } finally {
            cancelIfRunning(stdoutFuture);
            cancelIfRunning(stderrFuture);
            context.cleanupResources();
            deleteScriptFile(scriptFile.toPath());
        }
    }

    protected Process startProcess(ProcessBuilder builder) throws IOException {
        return builder.start();
    }

    private void deleteScriptFile(Path file) throws IOException {
        // Windows can briefly retain a script handle after process termination.
        // Keep cleanup bounded and preserve cancellation while waiting for release.
        boolean interrupted = Thread.interrupted();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        try {
            while (true) {
                try {
                    Files.deleteIfExists(file);
                    return;
                } catch (FileSystemException failure) {
                    if (System.nanoTime() >= deadline) {
                        throw failure;
                    }
                    try {
                        TimeUnit.MILLISECONDS.sleep(20);
                    } catch (InterruptedException interruption) {
                        interrupted = true;
                    }
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Charset resolveCharset(String preferred, String legacy) {
        String charset = StringUtils.isNotBlank(preferred) ? preferred : legacy;
        return StringUtils.isBlank(charset) ? StandardCharsets.UTF_8 : Charset.forName(charset);
    }

    private void cancelIfRunning(Future<?> future) {
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    private void waitWithoutTimeout(Process process) throws InterruptedException {
        process.waitFor();
    }
}
