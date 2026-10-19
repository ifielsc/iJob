// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.script;

import com.ifielsc.ijob.common.exception.StopException;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.task.TaskResult;
import com.ifielsc.ijob.core.execute.task.TaskRunner;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;
import com.ifielsc.ijob.plugin.common.config.PluginConfigCodec;
import com.ifielsc.ijob.plugin.common.result.PluginMessage;
import com.ifielsc.ijob.plugin.script.executor.RemoteSshScriptExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "app.plugin.script",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ScriptTaskRunner implements TaskRunner {
    private final ScriptExecutorRegistry registry;
    private final ScriptTaskConfigValidator configValidator;
    private final PluginConfigCodec configCodec;
    private final RemoteSshScriptExecutor remoteSshScriptExecutor;

    @Override
    public PluginType supportType() {
        return PluginType.SCRIPT;
    }

    @Override
    public TaskResult run(TaskExecutionSpec spec, TaskContext context) {
        boolean executionReady = false;
        try {
            ScriptTaskConfig config =
                    configCodec.read(spec.getPluginConfig(), ScriptTaskConfig.class, "Script");
            configValidator.validate(config);
            config.setScript(context.resolveScript(config.getScript()));
            configValidator.validate(config);
            executionReady = true;
            ScriptExecutionMode mode =
                    config.getExecutionMode() == null
                            ? ScriptExecutionMode.LOCAL
                            : config.getExecutionMode();
            context.logInfo(
                    "Script execution started, mode=" + mode + ", engine=" + config.getEngine());
            ScriptResult result =
                    mode == ScriptExecutionMode.SSH
                            ? remoteSshScriptExecutor.execute(config, spec, context)
                            : registry.get(config.getEngine()).execute(config, spec, context);
            context.logInfo("Script execution completed, exitCode=" + result.getExitCode());
            return result.getExitCode() == 0
                    ? TaskResult.success()
                    : TaskResult.fail(PluginMessage.SCRIPT_EXIT_NONZERO);
        } catch (TimeoutException e) {
            context.logWarn("Script execution timed out");
            return TaskResult.timeout(PluginMessage.SCRIPT_TIMEOUT);
        } catch (StopException e) {
            return TaskResult.stop();
        } catch (Exception e) {
            if (e instanceof com.ifielsc.ijob.common.exception.BusinessException business
                    && com.ifielsc.ijob.core.error.CoreMessage.SSH_HOST_KEY_MISMATCH
                            .errorCode()
                            .equals(business.getErrorCode())) {
                context.logError(business.getMessage());
                return TaskResult.failure(business, PluginMessage.SCRIPT_FAILED);
            }
            context.logError("Script execution failed, type=" + e.getClass().getSimpleName());
            log.error(
                    "Script execution failed, instanceId={}",
                    context.getInstanceId(),
                    com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(e));
            return executionReady
                    ? TaskResult.fail(PluginMessage.SCRIPT_FAILED)
                    : TaskResult.failure(e, PluginMessage.SCRIPT_FAILED);
        }
    }

    @Override
    public void stop(TaskExecutionSpec spec, TaskContext context) {
        context.cleanupResources();
    }
}
