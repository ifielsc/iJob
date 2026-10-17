package com.ifelio.ijob.plugin.push;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.exception.StopException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.core.execute.TimeoutExceptionClassifier;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.execute.task.TaskResult;
import com.ifelio.ijob.core.execute.task.TaskRunner;
import com.ifelio.ijob.core.resource.ResourceResolver;
import com.ifelio.ijob.core.task.definition.TaskExecutionSpec;
import com.ifelio.ijob.plugin.common.config.PluginConfigCodec;
import com.ifelio.ijob.plugin.common.result.PluginMessage;
import com.ifelio.ijob.plugin.push.compress.CompressionRegistry;
import com.ifelio.ijob.plugin.push.publisher.PayloadQuota;
import com.ifelio.ijob.plugin.push.publisher.PublishMetrics;
import com.ifelio.ijob.plugin.push.publisher.PublisherChainBuilder;
import com.ifelio.ijob.plugin.push.publisher.PushPublisher;
import com.ifelio.ijob.plugin.push.reader.JdbcStreamReader;
import com.ifelio.ijob.plugin.push.resolver.PushPublisherResolver;
import com.ifelio.ijob.plugin.push.retry.RetryPolicy;
import com.ifelio.ijob.plugin.push.serializer.MessageSerializerRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "app.plugin.push",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PushTaskRunner implements TaskRunner {
    private final ResourceResolver resourceResolver;
    private final PushPublisherResolver publisherResolver;
    private final PublisherChainBuilder chainBuilder;
    private final MessageSerializerRegistry serializerRegistry;
    private final CompressionRegistry compressionRegistry;
    private final PushTaskConfigValidator configValidator;
    private final PluginConfigCodec configCodec;

    @Override
    public PluginType supportType() {
        return PluginType.PUSH;
    }

    @Override
    public TaskResult run(TaskExecutionSpec spec, TaskContext context) {
        long startedAt = System.nanoTime();
        PayloadQuota quota = null;
        PublishMetrics metrics = null;
        try {
            PushTaskConfig config =
                    configCodec.read(spec.getPluginConfig(), PushTaskConfig.class, "Push");
            resolveRuntimeValues(config, context);
            configValidator.validate(config);
            context.checkExecution();
            context.logInfo(
                    "Push pipeline started, sourceResourceId="
                            + config.getSourceResourceId()
                            + ", batchSize="
                            + config.getBatchSize()
                            + ", maxInflight="
                            + config.getMaxInflight());

            var serializer = serializerRegistry.get(config.getSerializer());
            var compression =
                    "none".equals(config.getCompression())
                            ? null
                            : compressionRegistry.get(config.getCompression());

            PushPublisher target = publisherResolver.resolve(config.getTarget());
            quota =
                    new PayloadQuota(
                            config.getMaxMessageBytes(),
                            config.getMaxMessages(),
                            config.getMaxTotalPayloadBytes());
            metrics = new PublishMetrics();

            PushPublisher publisher =
                    chainBuilder.build(
                            target,
                            serializer,
                            compression,
                            RetryPolicy.builder().maxRetries(config.getRetryTimes()).build(),
                            config.getMaxInflight(),
                            config.getPublishTimeoutSeconds(),
                            quota,
                            metrics);
            context.registerResource(publisher);

            DataSource ds = resourceResolver.getDataSource(config.getSourceResourceId());
            context.progress(10);

            try (JdbcStreamReader reader =
                    new JdbcStreamReader(
                            ds,
                            config.getSql(),
                            config.getFetchSize(),
                            resource -> {
                                context.registerResource(resource);
                                context.checkStop();
                                context.checkInterrupted();
                            })) {
                context.progress(20);
                List<PushMessage> buffer = new ArrayList<>();
                while (reader.hasNext()) {
                    checkExecution(context);

                    Map<String, Object> row = reader.next();
                    buffer.add(PushMessage.builder().payload(row).build());

                    if (buffer.size() >= config.getBatchSize()) {
                        publisher.publishBatch(buffer);
                        buffer.clear();
                    }
                }

                if (!buffer.isEmpty()) {
                    publisher.publishBatch(buffer);
                }
            }
            context.progress(80);
            context.logInfo("Push source exhausted; waiting for publish acknowledgements");

            checkExecution(context);
            publisher.flush();
            context.logInfo("Push publisher flush completed");
            checkExecution(context);
            context.progress(95);
            return TaskResult.success();
        } catch (StopException e) {
            return TaskResult.stop();
        } catch (Exception e) {
            try {
                context.checkStop();
            } catch (StopException stopException) {
                return TaskResult.stop();
            }
            context.logError("push failed type=" + e.getClass().getSimpleName());
            log.error(
                    "Push failed, instanceId={}",
                    context.getInstanceId(),
                    com.ifelio.ijob.common.diagnostic.SafeDiagnostic.sanitized(e));
            if (TimeoutExceptionClassifier.isTimeout(e)) {
                return TaskResult.timeout(PluginMessage.PUSH_TIMEOUT);
            }
            boolean actionableFailure =
                    e instanceof IllegalArgumentException
                            || e instanceof BusinessException business
                                    && business.getCode() == ResultCode.PARAM_ERROR;
            return actionableFailure
                    ? TaskResult.failure(e, PluginMessage.PUSH_FAILED)
                    : TaskResult.fail(PluginMessage.PUSH_FAILED);
        } finally {
            context.cleanupResources();
            publishSummary(context, quota, metrics, startedAt);
        }
    }

    @Override
    public void stop(TaskExecutionSpec spec, TaskContext context) {
        context.cleanupResources();
    }

    private void checkExecution(TaskContext context) {
        context.checkExecution();
    }

    private void publishSummary(
            TaskContext context, PayloadQuota quota, PublishMetrics metrics, long startedAt) {
        if (quota == null || metrics == null) {
            return;
        }
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
        context.putVariable("push.attemptedCount", metrics.getAttemptedCount());
        context.putVariable("push.messageCount", quota.getMessageCount());
        context.putVariable("push.successCount", metrics.getSuccessCount());
        context.putVariable("push.failedCount", metrics.getFailedCount());
        context.putVariable("push.payloadBytes", quota.getPayloadBytes());
        context.putVariable("push.elapsedMillis", elapsedMillis);
        context.logInfo(
                "push summary attempted="
                        + metrics.getAttemptedCount()
                        + " accepted="
                        + quota.getMessageCount()
                        + " succeeded="
                        + metrics.getSuccessCount()
                        + " failed="
                        + metrics.getFailedCount()
                        + " payloadBytes="
                        + quota.getPayloadBytes()
                        + " elapsedMillis="
                        + elapsedMillis);
    }

    private void resolveRuntimeValues(PushTaskConfig config, TaskContext context) {
        if (config == null) {
            return;
        }
        config.setSql(context.resolve(config.getSql()));
        if (config.getTarget() == null || config.getTarget().getProperties() == null) {
            return;
        }
        Map<String, Object> resolved = new LinkedHashMap<>(config.getTarget().getProperties());
        resolveTargetProperty(resolved, "exchange", context);
        resolveTargetProperty(resolved, "routingKey", context);
        resolveTargetProperty(resolved, "topic", context);
        config.getTarget().setProperties(resolved);
    }

    private void resolveTargetProperty(
            Map<String, Object> properties, String name, TaskContext context) {
        Object value = properties.get(name);
        if (value instanceof String text) {
            properties.put(name, context.resolve(text));
        }
    }
}
