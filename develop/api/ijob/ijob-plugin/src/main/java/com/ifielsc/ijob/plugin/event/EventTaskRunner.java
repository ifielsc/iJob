package com.ifielsc.ijob.plugin.event;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.exception.StopException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.EventAction;
import com.ifielsc.ijob.contract.enums.EventSourceType;
import com.ifielsc.ijob.contract.enums.EventWaitStatus;
import com.ifielsc.ijob.contract.enums.EventWaitType;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.core.event.EventPublishCommand;
import com.ifielsc.ijob.core.event.EventReceiptResult;
import com.ifielsc.ijob.core.event.EventReceiptService;
import com.ifielsc.ijob.core.event.EventWaitRegistration;
import com.ifielsc.ijob.core.event.EventWaitRegistrationResult;
import com.ifielsc.ijob.core.event.EventWaitService;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.task.TaskResult;
import com.ifielsc.ijob.core.execute.task.TaskRunner;
import com.ifielsc.ijob.core.resource.ResourceClientLease;
import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.core.resource.factory.file.client.FileClient;
import com.ifielsc.ijob.core.resource.factory.file.client.FileClientException;
import com.ifielsc.ijob.core.resource.factory.file.client.FileInfo;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;
import com.ifielsc.ijob.plugin.common.config.PluginConfigCodec;
import com.ifielsc.ijob.plugin.common.result.PluginMessage;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
@ConditionalOnProperty(
        prefix = "app.plugin.event",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class EventTaskRunner implements TaskRunner {
    private static final int MAX_SCANNED_DIRECTORIES = 10_000;
    private static final int DEFAULT_MAX_DEPTH = 16;
    private static final int DEFAULT_MAX_SCANNED_ENTRIES = 100_000;
    private static final int DEFAULT_SCAN_TIMEOUT_SECONDS = 60;

    private final EventReceiptService eventReceiptService;
    private final EventWaitService eventWaitService;
    private final ResourceResolver resourceResolver;
    private final PluginConfigCodec configCodec;

    @Override
    public PluginType supportType() {
        return PluginType.EVENT;
    }

    @Override
    public TaskResult run(TaskExecutionSpec spec, TaskContext context) {
        try {
            EventTaskConfig config =
                    configCodec.read(spec.getPluginConfig(), EventTaskConfig.class, "Event");
            EventTaskConfigValidator.validate(config, spec.getTimeout());
            EventAction action =
                    config.getAction() == null ? EventAction.PUBLISH : config.getAction();
            context.checkExecution();
            return switch (action) {
                case PUBLISH -> publish(config, context);
                case WAIT_EVENT -> waitEvent(config, context);
                case WAIT_APPROVAL -> waitApproval(config, context);
                case PROBE_FILE -> probeFile(config, context);
            };
        } catch (StopException exception) {
            return TaskResult.stop();
        } catch (FileProbeTimeoutException exception) {
            return TaskResult.timeout(PluginMessage.FILE_PROBE_TIMEOUT);
        } catch (BusinessException exception) {
            diagnostic(context, exception);
            return businessFailure(exception);
        } catch (SecurityException exception) {
            diagnostic(context, exception);
            return TaskResult.fail(PluginMessage.EVENT_ACCESS_DENIED);
        } catch (IllegalArgumentException exception) {
            diagnostic(context, exception);
            return TaskResult.fail(PluginMessage.EVENT_CONFIG_INVALID);
        } catch (FileNotFoundException | NoSuchFileException | AccessDeniedException exception) {
            diagnostic(context, exception);
            return TaskResult.fail(PluginMessage.FILE_PROBE_UNAVAILABLE);
        } catch (FileClientException exception) {
            diagnostic(context, exception);
            return exception.isRetryable()
                    ? TaskResult.retryableFail(PluginMessage.FILE_PROBE_FAILED)
                    : TaskResult.fail(PluginMessage.FILE_PROBE_FAILED);
        } catch (IOException exception) {
            diagnostic(context, exception);
            return TaskResult.retryableFail(PluginMessage.FILE_PROBE_FAILED);
        } catch (TransientDataAccessException exception) {
            diagnostic(context, exception);
            return TaskResult.retryableFail(PluginMessage.EVENT_SERVICE_FAILED);
        } catch (RuntimeException exception) {
            diagnostic(context, exception);
            return TaskResult.fail(PluginMessage.EVENT_FAILED);
        }
    }

    private TaskResult publish(EventTaskConfig config, TaskContext context) {
        String eventCode = config.getEventCode();
        if (config.isSuppressSameInbound() && context.containsEvent(eventCode)) {
            context.logInfo("Event publish skipped: matching inbound event");
            return TaskResult.success(PluginMessage.EVENT_INBOUND_MATCHED);
        }
        EventReceiptResult result =
                eventReceiptService.receive(
                        command(
                                config,
                                context,
                                EventSourceType.TASK,
                                "TASK:" + context.getInstanceId(),
                                defaultText(
                                        resolve(config.getIdempotencyKey(), context), eventCode),
                                parseOccurredAt(resolve(config.getOccurredAt(), context)),
                                context.resolveObject(config.getPayload())));
        context.addEvent(eventCode);
        context.logInfo("Event receipt persisted, duplicate=" + result.duplicate());
        return TaskResult.success(
                result.duplicate()
                        ? PluginMessage.EVENT_ALREADY_PUBLISHED
                        : PluginMessage.EVENT_PUBLISHED);
    }

    private TaskResult waitEvent(EventTaskConfig config, TaskContext context) {
        EventWaitRegistrationResult result =
                eventWaitService.register(
                        context.getInstanceId(),
                        new EventWaitRegistration(
                                EventWaitType.EVENT,
                                config.getEventCode(),
                                resolve(config.getBusinessKey(), context),
                                resolve(config.getCorrelationKey(), context),
                                config.getTimeoutSeconds(),
                                config.getLookbackSeconds(),
                                null,
                                context.getInboundEventId()));
        context.logInfo("Event wait registration completed, status=" + result.status());
        return waitResult(result, false, config.getTimeoutSeconds() == null);
    }

    private TaskResult waitApproval(EventTaskConfig config, TaskContext context) {
        EventWaitRegistrationResult result =
                eventWaitService.register(
                        context.getInstanceId(),
                        new EventWaitRegistration(
                                EventWaitType.APPROVAL,
                                null,
                                null,
                                null,
                                config.getTimeoutSeconds(),
                                0,
                                config.getAllowedOperatorIds()));
        context.logInfo("Approval wait registration completed, status=" + result.status());
        return waitResult(result, true, config.getTimeoutSeconds() == null);
    }

    private TaskResult probeFile(EventTaskConfig config, TaskContext context) throws IOException {
        context.logInfo("File probe started, resourceId=" + config.getFileResourceId());
        String root = context.resolve(config.getFilePath());
        if (root == null || root.isBlank() || root.length() > 1024) {
            throw new IllegalArgumentException("File probe path is invalid");
        }
        PortableFileNameMatcher matcher =
                PortableFileNameMatcher.compile(
                        defaultText(config.getFilePattern(), "*"),
                        !Boolean.FALSE.equals(config.getFileCaseSensitive()));
        LocalDateTime stableBefore =
                LocalDateTime.now()
                        .minusSeconds(
                                config.getMinStableAgeSeconds() == null
                                        ? 30
                                        : config.getMinStableAgeSeconds());
        int limit = config.getMaxMatches() == null ? 100 : config.getMaxMatches();
        int maxDepth = config.getMaxDepth() == null ? DEFAULT_MAX_DEPTH : config.getMaxDepth();
        int maxEntries =
                config.getMaxScannedEntries() == null
                        ? DEFAULT_MAX_SCANNED_ENTRIES
                        : config.getMaxScannedEntries();
        int scanTimeout =
                config.getScanTimeoutSeconds() == null
                        ? DEFAULT_SCAN_TIMEOUT_SECONDS
                        : config.getScanTimeoutSeconds();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(scanTimeout);
        int published = 0;
        int duplicates = 0;
        int scannedEntries = 0;

        ResourceClientLease<FileClient> lease =
                resourceResolver.leaseFileClient(config.getFileResourceId());
        context.registerResource(lease);
        try (lease) {
            FileClient client = lease.getClient();
            Queue<ProbeDirectory> directories = new ArrayDeque<>();
            directories.add(new ProbeDirectory(root, 0));
            Set<String> visited = new HashSet<>();
            int scannedDirectories = 0;
            while (!directories.isEmpty() && published < limit) {
                checkProbe(context, deadline);
                ProbeDirectory current = directories.remove();
                String directory = normalizeQueuePath(current.path());
                if (!visited.add(directory)) {
                    continue;
                }
                if (++scannedDirectories > MAX_SCANNED_DIRECTORIES) {
                    throw new IllegalArgumentException("File probe directory limit exceeded");
                }
                List<FileInfo> entries = client.list(directory);
                checkProbe(context, deadline);
                for (FileInfo entry : entries) {
                    checkProbe(context, deadline);
                    if (++scannedEntries > maxEntries) {
                        throw new IllegalArgumentException("File probe entry limit exceeded");
                    }
                    validateEntryName(entry.getName());
                    String path = join(directory, entry.getName());
                    if (entry.isDirectory()) {
                        if (config.isRecursive()) {
                            if (current.depth() >= maxDepth) {
                                throw new IllegalArgumentException(
                                        "File probe depth limit exceeded");
                            }
                            directories.add(new ProbeDirectory(path, current.depth() + 1));
                        }
                        continue;
                    }
                    if (!matcher.matches(entry.getName())
                            || entry.getLastModified() == null
                            || entry.getLastModified().isAfter(stableBefore)) {
                        continue;
                    }
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("resourceId", config.getFileResourceId());
                    payload.put("path", path);
                    payload.put("size", entry.getSize());
                    payload.put("lastModified", entry.getLastModified());
                    String idempotency =
                            fileIdempotency(
                                    config.getEventCode(),
                                    path,
                                    entry.getSize(),
                                    entry.getLastModified());
                    EventReceiptResult receipt =
                            eventReceiptService.receive(
                                    command(
                                            config,
                                            context,
                                            EventSourceType.FILE,
                                            "FILE:" + config.getFileResourceId(),
                                            idempotency,
                                            entry.getLastModified(),
                                            payload));
                    if (receipt.duplicate()) {
                        duplicates++;
                        continue;
                    }
                    published++;
                    if (published >= limit) {
                        break;
                    }
                }
            }
        }
        context.logInfo(
                "File probe completed, scanned="
                        + scannedEntries
                        + ", published="
                        + published
                        + ", duplicates="
                        + duplicates);
        return TaskResult.success(PluginMessage.FILE_EVENTS_PUBLISHED, published, duplicates);
    }

    @Override
    public void stop(TaskExecutionSpec spec, TaskContext context) {
        if (context != null) {
            context.cleanupResources();
        }
    }

    private TaskResult waitResult(
            EventWaitRegistrationResult result, boolean approval, boolean indefinite) {
        EventWaitStatus status = result.status();
        return switch (status) {
            case WAITING ->
                    TaskResult.waitingEvent(
                            approval
                                    ? (indefinite
                                            ? PluginMessage.WAITING_APPROVAL_INDEFINITE
                                            : PluginMessage.WAITING_APPROVAL)
                                    : (indefinite
                                            ? PluginMessage.WAITING_EVENT_INDEFINITE
                                            : PluginMessage.WAITING_EVENT));
            case MATCHED -> TaskResult.success(PluginMessage.EVENT_ALREADY_ARRIVED);
            case APPROVED -> TaskResult.success(PluginMessage.APPROVAL_ALREADY_APPROVED);
            case REJECTED -> TaskResult.fail(PluginMessage.APPROVAL_REJECTED);
            case TIMED_OUT -> TaskResult.timeout(PluginMessage.EVENT_WAIT_TIMEOUT);
            case WITHDRAWN -> TaskResult.stop(PluginMessage.APPROVAL_WITHDRAWN);
            case STOPPED -> TaskResult.stop(PluginMessage.EVENT_WAIT_STOPPED);
        };
    }

    private TaskResult businessFailure(BusinessException exception) {
        ResultCode code = exception.getCode();
        if (code == ResultCode.SERVICE_UNAVAILABLE || code == ResultCode.TOO_MANY_REQUESTS) {
            return TaskResult.retryableFail(PluginMessage.EVENT_SERVICE_FAILED);
        }
        if (!exception.isCustomMessage()) {
            TaskResult result = TaskResult.fail(PluginMessage.EVENT_FAILED);
            result.setMessage(exception.getMessage());
            result.setMessageMetadata(
                    com.ifielsc.ijob.core.runtime.RuntimeErrorMetadata.from(exception));
            return result;
        }
        return TaskResult.fail(PluginMessage.EVENT_FAILED);
    }

    private void diagnostic(TaskContext context, Exception exception) {
        log.warn(
                "Event failed, instanceId={}, diagnostic={}",
                context.getInstanceId(),
                com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.describe(exception));
    }

    private void checkProbe(TaskContext context, long deadline) {
        context.checkExecution();
        if (System.nanoTime() - deadline >= 0) {
            throw new FileProbeTimeoutException("File probe scan timeout");
        }
    }

    private void validateEntryName(String name) {
        if (name == null
                || name.isBlank()
                || ".".equals(name)
                || "..".equals(name)
                || name.indexOf('/') >= 0
                || name.indexOf('\\') >= 0
                || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("File probe returned an invalid child name");
        }
    }

    private String normalizeQueuePath(String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private EventPublishCommand command(
            EventTaskConfig config,
            TaskContext context,
            EventSourceType sourceType,
            String sourceKey,
            String idempotencyKey,
            LocalDateTime occurredAt,
            Object payload) {
        return new EventPublishCommand(
                config.getEventCode(),
                sourceType,
                sourceKey,
                resolve(config.getBusinessKey(), context),
                resolve(config.getCorrelationKey(), context),
                idempotencyKey,
                occurredAt,
                resolve(config.getSummary(), context),
                payload);
    }

    private String resolve(String value, TaskContext context) {
        return value == null ? null : context.resolve(value);
    }

    private LocalDateTime parseOccurredAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Event occurredAt must be ISO local date-time", exception);
        }
    }

    private String join(String parent, String child) {
        if (parent.endsWith("/") || parent.endsWith("\\")) {
            return parent + child;
        }
        return parent + "/" + child;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String fileIdempotency(
            String eventCode, String path, Long size, LocalDateTime lastModified) {
        String identity = eventCode + "\0" + path + ":" + size + ":" + lastModified;
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ProbeDirectory(String path, int depth) {}

    private static final class FileProbeTimeoutException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private FileProbeTimeoutException(String message) {
            super(message);
        }
    }
}
