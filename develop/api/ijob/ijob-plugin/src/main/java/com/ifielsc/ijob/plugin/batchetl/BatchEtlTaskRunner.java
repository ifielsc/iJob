package com.ifielsc.ijob.plugin.batchetl;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.exception.StopException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.core.execute.TimeoutExceptionClassifier;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.task.TaskResult;
import com.ifielsc.ijob.core.execute.task.TaskRunner;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;
import com.ifielsc.ijob.plugin.batchetl.config.BatchEtlTaskConfig;
import com.ifielsc.ijob.plugin.batchetl.config.BatchEtlTaskConfigValidator;
import com.ifielsc.ijob.plugin.common.config.PluginConfigCodec;
import com.ifielsc.ijob.plugin.common.result.PluginMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.sql.SQLNonTransientException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "app.plugin.batch-etl",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BatchEtlTaskRunner implements TaskRunner {
    private final PluginConfigCodec configCodec;
    private final BatchEtlTaskConfigValidator configValidator;
    private final BatchEtlExecutor executor;

    @Override
    public PluginType supportType() {
        return PluginType.BATCH_ETL;
    }

    @Override
    public TaskResult run(TaskExecutionSpec spec, TaskContext context) {
        long startedAt = System.nanoTime();
        boolean configValidated = false;
        try {
            BatchEtlTaskConfig config = parseAndResolve(spec.getPluginConfig(), context);
            configValidator.validate(config, spec.getRetryTimes());
            configValidated = true;
            BatchEtlResult result = executor.execute(config, context);
            publishSummary(result, startedAt, context);
            return TaskResult.success();
        } catch (StopException exception) {
            publishFailureSummary(exception, context);
            return TaskResult.stop();
        } catch (Exception exception) {
            try {
                context.checkStop();
            } catch (StopException stopException) {
                publishFailureSummary(exception, context);
                return TaskResult.stop();
            }
            log.error(
                    "Batch ETL execution failed, instanceId={}",
                    context.getInstanceId(),
                    com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
            publishFailureSummary(exception, context);
            context.logError(
                    "Batch ETL execution failed type=" + exception.getClass().getSimpleName());
            if (TimeoutExceptionClassifier.isTimeout(exception)) {
                return TaskResult.timeout(PluginMessage.BATCH_ETL_TIMEOUT);
            }
            if (exception instanceof IllegalArgumentException invalid) {
                return TaskResult.failure(invalid, PluginMessage.BATCH_ETL_CONFIG_INVALID);
            }
            if (isPermanent(exception)) {
                return TaskResult.fail(PluginMessage.BATCH_ETL_RESOURCE_INVALID);
            }
            return configValidated
                    ? TaskResult.retryableFail(PluginMessage.BATCH_ETL_FAILED)
                    : TaskResult.fail(PluginMessage.BATCH_ETL_CONFIG_INVALID);
        }
    }

    private BatchEtlTaskConfig parseAndResolve(String json, TaskContext context) {
        BatchEtlTaskConfig unresolved =
                configCodec.read(json, BatchEtlTaskConfig.class, "Batch ETL");
        configValidator.validateUnresolved(unresolved);
        Map<String, Object> tree = configCodec.toMap(unresolved, "Batch ETL");
        Object resolved = context.resolveObject(tree);
        BatchEtlTaskConfig config =
                configCodec.convert(resolved, BatchEtlTaskConfig.class, "Batch ETL");
        if (configCodec.write(config, "Batch ETL").contains("${")) {
            throw new IllegalArgumentException("Batch ETL config contains unresolved variables");
        }
        return config;
    }

    private void publishSummary(BatchEtlResult result, long startedAt, TaskContext context) {
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        context.putVariable("batchEtl.extractedRows", result.extracted());
        context.putVariable("batchEtl.insertedRows", result.inserted());
        context.putVariable("batchEtl.updatedRows", result.updated());
        context.putVariable("batchEtl.skippedRows", result.skipped());
        context.putVariable("batchEtl.tableCount", result.tables().size());
        context.putVariable("batchEtl.elapsedMillis", elapsedMillis);
        context.putVariable("batchEtl.partialCommitPossible", false);
        List<Map<String, Object>> tableResults =
                result.tables().stream().map(this::tableResult).toList();
        context.putVariable("batchEtl.tableResults", tableResults);
        context.logInfo(
                "Batch ETL completed tables="
                        + result.tables().size()
                        + ", extracted="
                        + result.extracted()
                        + ", inserted="
                        + result.inserted()
                        + ", updated="
                        + result.updated()
                        + ", skipped="
                        + result.skipped());
    }

    private void publishFailureSummary(Exception exception, TaskContext context) {
        long committedBatches = number(context.getVariable("batchEtl.committedBatches"));
        context.putVariable("batchEtl.committedBatches", committedBatches);
        boolean partial =
                committedBatches > 0
                        || Boolean.TRUE.equals(
                                context.getVariable("batchEtl.nonTransactionalClearAttempted"))
                        || Boolean.TRUE.equals(
                                context.getVariable("batchEtl.commitOutcomeUnknown"));
        context.putVariable("batchEtl.partialCommitPossible", partial);
        context.putVariable("batchEtl.failureType", exception.getClass().getSimpleName());
        if (partial) {
            context.logError(
                    "Batch ETL failed after committed batches="
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

    private Map<String, Object> tableResult(BatchEtlResult.TableResult result) {
        return Map.of(
                "sourceTable", result.sourceTable(),
                "targetTable", result.targetTable(),
                "extracted", result.extracted(),
                "inserted", result.inserted(),
                "updated", result.updated(),
                "skipped", result.skipped());
    }

    @Override
    public void stop(TaskExecutionSpec spec, TaskContext context) {
        context.cleanupResources();
    }
}
