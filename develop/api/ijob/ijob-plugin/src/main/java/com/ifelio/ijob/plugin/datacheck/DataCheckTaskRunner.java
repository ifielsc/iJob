package com.ifelio.ijob.plugin.datacheck;

import com.ifelio.ijob.common.diagnostic.SafeDiagnostic;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.exception.StopException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.core.execute.TimeoutExceptionClassifier;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.execute.task.TaskResult;
import com.ifelio.ijob.core.execute.task.TaskRunner;
import com.ifelio.ijob.core.task.definition.TaskExecutionSpec;
import com.ifelio.ijob.plugin.common.config.PluginConfigCodec;
import com.ifelio.ijob.plugin.common.result.PluginMessage;
import com.ifelio.ijob.plugin.datacheck.config.DataCheckConfig;
import com.ifelio.ijob.plugin.datacheck.model.DataCheckContext;
import com.ifelio.ijob.plugin.datacheck.model.DataCheckResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.sql.SQLNonTransientException;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "app.plugin.data-check",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DataCheckTaskRunner implements TaskRunner {
    private final PluginConfigCodec configCodec;
    private final DataCheckPipeline pipeline;
    private final DataCheckConfigValidator configValidator;

    @Override
    public PluginType supportType() {
        return PluginType.DATA_CHECK;
    }

    @Override
    public TaskResult run(TaskExecutionSpec spec, TaskContext context) {
        boolean configValidated = false;
        try {
            DataCheckConfig config =
                    configCodec.read(spec.getPluginConfig(), DataCheckConfig.class, "Data check");
            resolveRuntimeValues(config, context);
            configValidator.validate(config);
            configValidated = true;
            context.checkExecution();
            DataCheckContext checkContext =
                    DataCheckContext.builder()
                            .instanceId(context.getInstanceId())
                            .resourceId(config.getResourceId())
                            .checks(config.getChecks())
                            .taskContext(context)
                            .build();
            DataCheckResult result = pipeline.execute(config, checkContext);
            return result.isSuccess()
                    ? TaskResult.success(PluginMessage.DATA_CHECK_SUCCESS)
                    : TaskResult.fail(PluginMessage.DATA_CHECK_FAILED);
        } catch (StopException e) {
            return TaskResult.stop();
        } catch (Exception e) {
            try {
                context.checkStop();
            } catch (StopException stopException) {
                return TaskResult.stop();
            }
            context.logError("data check failed type=" + e.getClass().getSimpleName());
            log.error(
                    "Data check failed, instanceId={}",
                    context.getInstanceId(),
                    SafeDiagnostic.sanitized(e));
            if (TimeoutExceptionClassifier.isTimeout(e)) {
                return TaskResult.timeout(PluginMessage.DATA_CHECK_TIMEOUT);
            }
            boolean actionableConfigFailure =
                    !configValidated
                            && (e instanceof IllegalArgumentException
                                    || e instanceof BusinessException business
                                            && business.getCode() == ResultCode.PARAM_ERROR);
            if (actionableConfigFailure) {
                return TaskResult.failure(e, PluginMessage.DATA_CHECK_STATEMENT_INVALID);
            }
            if (e instanceof BusinessException business) {
                boolean transientFailure =
                        business.getCode() == ResultCode.SERVICE_UNAVAILABLE
                                || business.getCode() == ResultCode.TOO_MANY_REQUESTS;
                return transientFailure
                        ? TaskResult.retryableFail(PluginMessage.DATA_CHECK_FAILED)
                        : TaskResult.fail(PluginMessage.DATA_CHECK_RESOURCE_INVALID);
            }
            if (hasNonTransientSqlFailure(e)) {
                return TaskResult.fail(PluginMessage.DATA_CHECK_STATEMENT_INVALID);
            }
            return TaskResult.retryableFail(PluginMessage.DATA_CHECK_FAILED);
        } finally {
            context.cleanupResources();
        }
    }

    @Override
    public void stop(TaskExecutionSpec spec, TaskContext context) {
        context.cleanupResources();
    }

    private void resolveRuntimeValues(DataCheckConfig config, TaskContext context) {
        if (config == null || config.getChecks() == null) {
            return;
        }
        for (var item : config.getChecks()) {
            if (item == null) {
                continue;
            }
            item.setSql(context.resolve(item.getSql()));
            item.setExpected(context.resolveObject(item.getExpected()));
            item.setMinValue(context.resolveObject(item.getMinValue()));
            item.setMaxValue(context.resolveObject(item.getMaxValue()));
        }
    }

    private boolean hasNonTransientSqlFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SQLNonTransientException) {
                return true;
            }
        }
        return false;
    }
}
