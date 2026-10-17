package com.ifelio.ijob.plugin.script.executor;

import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.resource.ResourceClientLease;
import com.ifelio.ijob.core.resource.ResourceResolver;
import com.ifelio.ijob.core.resource.factory.ssh.client.SshClient;
import com.ifelio.ijob.core.resource.factory.ssh.client.SshCommand;
import com.ifelio.ijob.core.task.definition.TaskExecutionSpec;
import com.ifelio.ijob.plugin.script.ProcessStreamCollector;
import com.ifelio.ijob.plugin.script.ScriptEngine;
import com.ifelio.ijob.plugin.script.ScriptOutputAwaiter;
import com.ifelio.ijob.plugin.script.ScriptResult;
import com.ifelio.ijob.plugin.script.ScriptTaskConfig;

import io.micrometer.common.util.StringUtils;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Component
public class RemoteSshScriptExecutor {
    private final ResourceResolver resourceResolver;
    private final ExecutorService scriptExecutorPool;
    private final ProcessStreamCollector streamCollector;

    public RemoteSshScriptExecutor(
            ResourceResolver resourceResolver,
            @Qualifier("scriptExecutorPool") ExecutorService scriptExecutorPool,
            ProcessStreamCollector streamCollector) {
        this.resourceResolver = resourceResolver;
        this.scriptExecutorPool = scriptExecutorPool;
        this.streamCollector = streamCollector;
    }

    public ScriptResult execute(
            ScriptTaskConfig config, TaskExecutionSpec spec, TaskContext context) throws Exception {
        Charset scriptCharset = resolveCharset(config.getScriptCharset(), config.getCharset());
        Charset outputCharset = resolveCharset(config.getOutputCharset(), config.getCharset());
        Future<String> stdoutFuture = null;
        Future<String> stderrFuture = null;
        try (ResourceClientLease<SshClient> lease =
                resourceResolver.leaseSshClient(config.getSshResourceId())) {
            context.registerResource(lease);
            context.checkExecution();
            ByteArrayInputStream input =
                    new ByteArrayInputStream(config.getScript().getBytes(scriptCharset));
            SshCommand command = lease.getClient().openCommand(buildCommand(config), input);
            context.registerResource(command);
            context.checkExecution();
            stdoutFuture =
                    streamCollector.collect(
                            context, command.stdout(), "STDOUT", outputCharset, scriptExecutorPool);
            stderrFuture =
                    streamCollector.collect(
                            context, command.stderr(), "STDERR", outputCharset, scriptExecutorPool);
            while (!command.isFinished()) {
                context.checkExecution();
                Thread.sleep(50);
            }
            context.checkExecution();
            String stdout = ScriptOutputAwaiter.await(stdoutFuture, 5, TimeUnit.SECONDS);
            String stderr = ScriptOutputAwaiter.await(stderrFuture, 5, TimeUnit.SECONDS);
            return ScriptResult.builder()
                    .exitCode(command.exitCode())
                    .stdout(stdout)
                    .stderr(stderr)
                    .build();
        } finally {
            cancelIfRunning(stdoutFuture);
            cancelIfRunning(stderrFuture);
            context.cleanupResources();
        }
    }

    String buildCommand(ScriptTaskConfig config) {
        StringBuilder command = new StringBuilder();
        if (StringUtils.isNotBlank(config.getWorkingDir())) {
            command.append("cd ").append(shellQuote(config.getWorkingDir())).append(" && ");
        }
        if (config.getEnv() != null && !config.getEnv().isEmpty()) {
            command.append("env ");
            for (Map.Entry<String, String> entry : config.getEnv().entrySet()) {
                command.append(entry.getKey())
                        .append('=')
                        .append(shellQuote(entry.getValue()))
                        .append(' ');
            }
        }
        if (config.getEngine() == ScriptEngine.PYTHON) {
            command.append(config.getInterpreter()).append(" -");
        } else {
            command.append("/bin/sh -s");
        }
        return command.toString();
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
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
}
