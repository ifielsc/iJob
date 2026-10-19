package com.ifielsc.ijob.plugin.etl;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.exception.StopException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.core.execute.TimeoutExceptionClassifier;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.task.TaskResult;
import com.ifielsc.ijob.core.execute.task.TaskRunner;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;
import com.ifielsc.ijob.plugin.common.config.PluginConfigCodec;
import com.ifielsc.ijob.plugin.common.result.PluginMessage;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfigValidator;
import com.ifielsc.ijob.plugin.etl.incremental.EtlIncrementalCoordinator;
import com.ifielsc.ijob.plugin.etl.model.EtlContext;
import com.ifielsc.ijob.plugin.etl.model.EtlResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.sql.SQLNonTransientException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "app.plugin.etl",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class EtlTaskRunner implements TaskRunner {
    private final PluginConfigCodec configCodec;
    private final EtlTaskConfigValidator configValidator;
    private final EtlPipelineFactory pipelineFactory;
    private final EtlIncrementalCoordinator incrementalCoordinator;
    private final com.ifielsc.ijob.plugin.etl.message.EtlMessageRunner messageRunner;
    private final com.ifielsc.ijob.plugin.etl.incremental.EtlFileIncrementalRunner
            fileIncrementalRunner;

    @Override
    public PluginType supportType() {
        return PluginType.ETL;
    }

    @Override
    public TaskResult run(TaskExecutionSpec spec, TaskContext context) {
        long startedAt = System.nanoTime();
        boolean configValidated = false;
        EtlContext etlContext = null;
        try {
            EtlTaskConfig config = parseAndResolve(spec.getPluginConfig(), context);
            configValidator.validate(config, spec.getRetryTimes());
            configValidated = true;
            etlContext = new EtlContext(context, config);
            EtlResult result;
            if (config.messageSource()) {
                result = messageRunner.execute(etlContext);
            } else if (config.getSource().getType() == EtlTaskConfig.SourceType.FILE
                    && config.getIncremental().isEnabled()) {
                result = fileIncrementalRunner.execute(etlContext);
            } else {
                etlContext.setIncrementalSession(incrementalCoordinator.open(config, context));
                result = pipelineFactory.create(config).execute(etlContext);
            }
            publishSummary(result, startedAt, context);
            return TaskResult.success();
        } catch (StopException exception) {
            publishFailureSummary(etlContext, exception, context);
            return TaskResult.stop();
        } catch (Exception exception) {
            try {
                context.checkStop();
            } catch (StopException stopException) {
                publishFailureSummary(etlContext, stopException, context);
                return TaskResult.stop();
            }
            log.error(
                    "ETL execution failed, instanceId={}",
                    context.getInstanceId(),
                    com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
            publishFailureSummary(etlContext, exception, context);
            context.logError("ETL execution failed type=" + exception.getClass().getSimpleName());
            if (TimeoutExceptionClassifier.isTimeout(exception)) {
                return TaskResult.timeout(PluginMessage.ETL_TIMEOUT);
            }
            if (exception instanceof IllegalArgumentException invalid) {
                return TaskResult.failure(invalid, PluginMessage.ETL_CONFIG_INVALID);
            }
            if (isPermanent(exception)) {
                return TaskResult.fail(PluginMessage.ETL_RESOURCE_INVALID);
            }
            return configValidated
                    ? TaskResult.retryableFail(PluginMessage.ETL_FAILED)
                    : TaskResult.fail(PluginMessage.ETL_CONFIG_INVALID);
        }
    }

    private EtlTaskConfig parseAndResolve(String json, TaskContext context) {
        EtlTaskConfig unresolved = configCodec.read(json, EtlTaskConfig.class, "ETL");
        configValidator.validateUnresolved(unresolved);
        Map<String, Object> tree = configCodec.toMap(unresolved, "ETL");
        Object resolved = context.resolveObject(tree);
        EtlTaskConfig config = configCodec.convert(resolved, EtlTaskConfig.class, "ETL");
        if (configCodec.write(config, "ETL").contains("${")) {
            throw new IllegalArgumentException("ETL config contains unresolved variables");
        }
        return config;
    }

    private void publishSummary(EtlResult result, long startedAt, TaskContext context) {
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        context.putVariable("etl.extractedRows", result.getExtractCount());
        context.putVariable("etl.loadedRows", result.getLoadCount());
        context.putVariable("etl.invalidRows", result.getInvalidCount());
        context.putVariable("etl.insertedRows", result.getInsertedCount());
        context.putVariable("etl.updatedRows", result.getUpdatedCount());
        context.putVariable("etl.skippedRows", result.getSkippedCount());
        context.putVariable("etl.protectedRows", result.getProtectedCount());
        context.putVariable("etl.elapsedMillis", elapsedMillis);
        context.putVariable("etl.partialCommitPossible", false);
        context.logInfo(
                "ETL completed extracted="
                        + result.getExtractCount()
                        + ", loaded="
                        + result.getLoadCount()
                        + ", invalid="
                        + result.getInvalidCount()
                        + ", skipped="
                        + result.getSkippedCount());
    }

    private void publishFailureSummary(
            EtlContext etlContext, Exception exception, TaskContext context) {
        if (etlContext == null) {
            return;
        }
        long committedBatches = number(context.getVariable("etl.committedBatches"));
        context.putVariable("etl.extractedRows", etlContext.getExtractCount());
        context.putVariable("etl.loadedRows", etlContext.getLoadCount());
        context.putVariable("etl.invalidRows", etlContext.getValidateErrorCount());
        context.putVariable("etl.committedBatches", committedBatches);
        context.putVariable("etl.partialCommitPossible", committedBatches > 0);
        context.putVariable("etl.failureType", exception.getClass().getSimpleName());
        if (committedBatches > 0) {
            context.logError(
                    "ETL failed after committed batches="
                            + committedBatches
                            + "; retry may revisit committed data");
        }
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private boolean isPermanent(Exception exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof SQLNonTransientException) {
                return true;
            }
            if (current instanceof BusinessException business) {
                ResultCode code = business.getCode();
                return code != ResultCode.SERVICE_UNAVAILABLE
                        && code != ResultCode.TOO_MANY_REQUESTS;
            }
        }
        return false;
    }

    @Override
    public void stop(TaskExecutionSpec spec, TaskContext context) {
        context.cleanupResources();
    }
}
