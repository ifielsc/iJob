package com.ifielsc.ijob.plugin.servermonitor;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.exception.StopException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.core.execute.TimeoutExceptionClassifier;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.task.TaskResult;
import com.ifielsc.ijob.core.execute.task.TaskRunner;
import com.ifielsc.ijob.core.resource.factory.monitor.client.MonitorClientException;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;
import com.ifielsc.ijob.plugin.common.config.PluginConfigCodec;
import com.ifielsc.ijob.plugin.common.result.PluginMessage;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorConfig;
import com.ifielsc.ijob.plugin.servermonitor.rule.ApplicationMonitorRule;
import com.ifielsc.ijob.plugin.servermonitor.rule.DiskMonitorRule;
import com.ifielsc.ijob.plugin.servermonitor.rule.Threshold;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "app.plugin.server-monitor",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ServerMonitorTaskRunner implements TaskRunner {
    private final MonitorPipeline pipeline;
    private final ServerMonitorTaskConfigValidator configValidator;
    private final PluginConfigCodec configCodec;

    @Override
    public PluginType supportType() {
        return PluginType.SERVER_MONITOR;
    }

    @Override
    public TaskResult run(TaskExecutionSpec spec, TaskContext context) {
        boolean configValidated = false;
        try {
            MonitorConfig config =
                    configCodec.read(spec.getPluginConfig(), MonitorConfig.class, "Server monitor");
            resolveRuntimeValues(config, context);
            configValidator.validate(config);
            configValidated = true;
            pipeline.execute(config, context);
            return TaskResult.success();
        } catch (StopException e) {
            return TaskResult.stop();
        } catch (Exception e) {
            try {
                context.checkStop();
            } catch (StopException stopException) {
                return TaskResult.stop();
            }
            context.logError("server monitor failed type=" + e.getClass().getSimpleName());
            log.error(
                    "Server monitor failed, instanceId={}",
                    context.getInstanceId(),
                    com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(e));
            if (TimeoutExceptionClassifier.isTimeout(e)) {
                return TaskResult.timeout(PluginMessage.MONITOR_TIMEOUT);
            }
            if (e instanceof MonitorClientException clientFailure) {
                return clientFailure.isRetryable()
                        ? TaskResult.retryableFail(PluginMessage.MONITOR_FAILED)
                        : TaskResult.fail(PluginMessage.MONITOR_REJECTED);
            }
            if (e instanceof BusinessException business) {
                return isTransient(business.getCode())
                        ? TaskResult.retryableFail(PluginMessage.MONITOR_FAILED)
                        : TaskResult.fail(PluginMessage.MONITOR_RESOURCE_INVALID);
            }
            return !configValidated && e instanceof IllegalArgumentException
                    ? TaskResult.failure(e, PluginMessage.MONITOR_FAILED)
                    : TaskResult.retryableFail(PluginMessage.MONITOR_FAILED);
        } finally {
            context.cleanupResources();
        }
    }

    @Override
    public void stop(TaskExecutionSpec spec, TaskContext context) {
        context.cleanupResources();
    }

    private boolean isTransient(ResultCode code) {
        return code == ResultCode.SERVICE_UNAVAILABLE || code == ResultCode.TOO_MANY_REQUESTS;
    }

    private void resolveRuntimeValues(MonitorConfig config, TaskContext context) {
        if (config == null) {
            return;
        }
        if (config.getCollect() != null && config.getCollect().getApplications() != null) {
            config.getCollect()
                    .getApplications()
                    .forEach(
                            application -> {
                                if (application != null) {
                                    application.setName(context.resolve(application.getName()));
                                    application.setMatchValue(
                                            context.resolve(application.getMatchValue()));
                                }
                            });
        }
        if (config.getRules() == null) {
            return;
        }
        if (config.getRules().getApplications() != null) {
            for (ApplicationMonitorRule rule : config.getRules().getApplications()) {
                if (rule != null) {
                    rule.setName(context.resolve(rule.getName()));
                }
            }
        }
        resolveDiskPaths(config.getRules().getDisk(), context);
    }

    private void resolveDiskPaths(DiskMonitorRule rule, TaskContext context) {
        if (rule == null || rule.getPaths() == null) {
            return;
        }
        Map<String, Threshold> resolved = new LinkedHashMap<>();
        rule.getPaths()
                .forEach(
                        (path, threshold) -> {
                            String resolvedPath = context.resolve(path);
                            if (resolved.containsKey(resolvedPath)) {
                                throw new IllegalArgumentException(
                                        "resolved monitor disk paths must be unique");
                            }
                            resolved.put(resolvedPath, threshold);
                        });
        rule.setPaths(resolved);
    }
}
