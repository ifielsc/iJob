package com.ifelio.ijob.plugin.filetransfer;

import com.ifelio.ijob.common.exception.StopException;
import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.core.execute.TimeoutExceptionClassifier;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.execute.task.TaskResult;
import com.ifelio.ijob.core.execute.task.TaskRunner;
import com.ifelio.ijob.core.task.definition.TaskExecutionSpec;
import com.ifelio.ijob.plugin.common.config.PluginConfigCodec;
import com.ifelio.ijob.plugin.common.result.PluginMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "app.plugin.file-transfer",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class FileTransferTaskRunner implements TaskRunner {
    private final FileTransferService fileTransferService;
    private final FileTransferTaskConfigValidator configValidator;
    private final PluginConfigCodec configCodec;

    @Override
    public PluginType supportType() {
        return PluginType.FILE_TRANSFER;
    }

    @Override
    public TaskResult run(TaskExecutionSpec spec, TaskContext context) {
        long startedAt = System.nanoTime();
        try {
            FileTransferConfig config =
                    configCodec.read(
                            spec.getPluginConfig(), FileTransferConfig.class, "File transfer");
            configValidator.validate(config);
            resolveRuntimeValues(config, context);
            configValidator.validate(config);
            FileTransferResult result = fileTransferService.transfer(config, context);
            publishSummary(result, startedAt, context);

            return TaskResult.success();
        } catch (StopException e) {
            return TaskResult.stop();
        } catch (Exception exception) {
            try {
                context.checkStop();
            } catch (StopException stopException) {
                return TaskResult.stop();
            }
            log.error(
                    "file transfer failed, instanceId={}",
                    context.getInstanceId(),
                    com.ifelio.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
            context.logError("File transfer failed, type=" + exception.getClass().getSimpleName());

            if (exception
                    instanceof
                    com.ifelio.ijob.core.resource.factory.file.client
                            .FilePublicationUncertainException) {
                return TaskResult.fail(PluginMessage.TRANSFER_PUBLICATION_UNCERTAIN);
            }
            if (TimeoutExceptionClassifier.isTimeout(exception)) {
                return TaskResult.timeout(PluginMessage.TRANSFER_TIMEOUT);
            }
            return exception instanceof IllegalArgumentException
                    ? TaskResult.failure(exception, PluginMessage.TRANSFER_FAILED)
                    : TaskResult.fail(PluginMessage.TRANSFER_FAILED);
        }
    }

    private void resolveRuntimeValues(FileTransferConfig config, TaskContext context) {
        config.setSourcePath(context.resolve(config.getSourcePath()));
        config.setTargetPath(context.resolve(config.getTargetPath()));
        config.setArchiveName(context.resolve(config.getArchiveName()));
    }

    private void publishSummary(FileTransferResult result, long startedAt, TaskContext context) {
        long elapsedMillis =
                java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        context.putVariable("fileTransfer.sourceFileCount", result.sourceFileCount());
        context.putVariable("fileTransfer.outputFileCount", result.outputFileCount());
        context.putVariable("fileTransfer.uploadedFileCount", result.uploadedFileCount());
        context.putVariable("fileTransfer.skippedFileCount", result.skippedFileCount());
        context.putVariable("fileTransfer.deletedSourceFileCount", result.deletedSourceFileCount());
        context.putVariable("fileTransfer.elapsedMillis", elapsedMillis);
        context.logInfo(
                "file transfer completed sourceFiles="
                        + result.sourceFileCount()
                        + ", outputFiles="
                        + result.outputFileCount()
                        + ", uploadedFiles="
                        + result.uploadedFileCount()
                        + ", skippedFiles="
                        + result.skippedFileCount());
    }

    @Override
    public void stop(TaskExecutionSpec spec, TaskContext context) {
        context.cleanupResources();
    }
}
