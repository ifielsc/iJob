package com.ifelio.ijob.infra.coordination.command;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.RuntimeCommandStatus;
import com.ifelio.ijob.contract.enums.RuntimeCommandType;
import com.ifelio.ijob.contract.runtime.RuntimeCommandDTO;
import com.ifelio.ijob.infra.coordination.work.TriggerWorkClaim;
import com.ifelio.ijob.infra.mapper.coordination.RuntimeCommandMapper;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
public class DatabaseRuntimeCommandQueue {
    private final RuntimeCommandMapper mapper;
    private final AppProperties appProperties;
    private final com.ifelio.ijob.infra.coordination.RuntimeMutationGuard guard;

    @org.springframework.beans.factory.annotation.Autowired
    public DatabaseRuntimeCommandQueue(
            RuntimeCommandMapper mapper,
            AppProperties appProperties,
            com.ifelio.ijob.infra.coordination.RuntimeMutationGuard guard) {
        this.mapper = mapper;
        this.appProperties = appProperties;
        this.guard = guard;
    }

    public DatabaseRuntimeCommandQueue(RuntimeCommandMapper mapper, AppProperties appProperties) {
        this(mapper, appProperties, null);
    }

    @Transactional
    public RuntimeCommandDTO submit(
            String suppliedRequestId,
            Long instanceId,
            RuntimeCommandType commandType,
            String requestedBy) {
        if (guard != null)
            guard.lock(com.ifelio.ijob.infra.coordination.RuntimeMutationGuard.LIFECYCLE);
        String requestId = normalizeRequestId(suppliedRequestId);
        String actor = normalizeActor(requestedBy);
        int inserted =
                mapper.insertFromInstance(
                        requestId,
                        instanceId,
                        commandType,
                        actor,
                        micros(appProperties.getRuntime().getCommand().getTimeout()));
        RuntimeCommand existing = mapper.selectByRequestId(requestId);
        if (existing == null) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Instance not found");
        }
        if (!instanceId.equals(existing.getInstanceId())
                || commandType != existing.getCommandType()) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "Idempotency key belongs to another runtime command");
        }
        if (inserted == 1) {
            mapper.succeedInitiallySatisfied(requestId);
            mapper.rejectInitiallyInvalid(requestId);
            RuntimeCommand initialized = mapper.selectByRequestId(requestId);
            if (initialized != null
                    && initialized.getStatus() == RuntimeCommandStatus.PENDING
                    && initialized.getCommandType() == RuntimeCommandType.RETRY) {
                if (mapper.activateTriggerForRetry(requestId) != 1
                        || mapper.requeueWorkForCommand(requestId) != 1) {
                    throw BusinessException.localized(
                            ResultCode.CONFLICT, "Retry work cannot be reactivated");
                }
            } else {
                mapper.requeueWorkForCommand(requestId);
            }
        }
        RuntimeCommandDTO result = requireDto(requestId);
        result.setReplayed(inserted == 0);
        return result;
    }

    public RuntimeCommandDTO get(String requestId) {
        String normalized = normalizeExistingRequestId(requestId);
        mapper.expireByRequestId(normalized);
        return requireDto(normalized);
    }

    public List<String> listDispatchable(TriggerWorkClaim claim) {
        return List.copyOf(
                mapper.selectDispatchableRequestIds(
                        claim.triggerId(),
                        claim.fenceToken(),
                        appProperties.getRuntime().getCommand().getScanBatchSize()));
    }

    public List<RuntimeCommandCandidate> listDispatchableOwned(String leaseOwner) {
        if (leaseOwner == null || leaseOwner.isBlank()) {
            return List.of();
        }
        return List.copyOf(
                mapper.selectDispatchableOwned(
                        leaseOwner, appProperties.getRuntime().getCommand().getScanBatchSize()));
    }

    public int succeedCompletedStops(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Runtime command reconciliation limit is invalid");
        }
        return mapper.succeedCompletedStops(limit);
    }

    public int expirePending(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Runtime command expiration limit is invalid");
        }
        return mapper.expirePending(limit);
    }

    @Transactional
    public RuntimeCommandClaim claim(String requestId, TriggerWorkClaim workClaim) {
        String processingToken = UUID.randomUUID().toString();
        if (mapper.claim(
                        requestId,
                        workClaim.triggerId(),
                        workClaim.leaseOwner(),
                        processingToken,
                        workClaim.fenceToken())
                != 1) {
            return null;
        }
        RuntimeCommand command = mapper.selectByRequestId(requestId);
        if (command == null || !processingToken.equals(command.getProcessingToken())) {
            throw new IllegalStateException("Claimed runtime command cannot be read back");
        }
        return new RuntimeCommandClaim(
                command.getRequestId(),
                command.getInstanceId(),
                command.getRootInstanceId(),
                command.getTriggerId(),
                command.getCommandType(),
                command.getProcessingOwner(),
                command.getProcessingToken(),
                command.getProcessingFence());
    }

    @Transactional
    public boolean succeed(RuntimeCommandClaim claim, String message) {
        return finish(claim, RuntimeCommandStatus.SUCCEEDED, message);
    }

    @Transactional
    public boolean reject(RuntimeCommandClaim claim, String message) {
        return finish(claim, RuntimeCommandStatus.REJECTED, message);
    }

    @Transactional
    public boolean fail(RuntimeCommandClaim claim, String message) {
        return finish(claim, RuntimeCommandStatus.FAILED, message);
    }

    @Transactional
    public boolean defer(RuntimeCommandClaim claim, String message) {
        return mapper.defer(
                        claim.requestId(),
                        claim.processingToken(),
                        claim.processingFence(),
                        micros(appProperties.getRuntime().getCommand().getRetryDelay()),
                        summarize(message))
                == 1;
    }

    private boolean finish(
            RuntimeCommandClaim claim, RuntimeCommandStatus status, String resultMessage) {
        return mapper.finish(
                        claim.requestId(),
                        claim.processingToken(),
                        claim.processingFence(),
                        status,
                        summarize(resultMessage))
                == 1;
    }

    private RuntimeCommandDTO requireDto(String requestId) {
        RuntimeCommandDTO dto = mapper.selectDtoByRequestId(requestId);
        if (dto == null) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Runtime command not found");
        }
        return dto;
    }

    private String normalizeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return normalizeExistingRequestId(requestId);
    }

    private String normalizeExistingRequestId(String requestId) {
        String normalized = requestId == null ? null : requestId.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > 191) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "Runtime command request id is invalid");
        }
        return normalized;
    }

    private String normalizeActor(String actor) {
        String normalized = actor == null || actor.isBlank() ? "unknown" : actor.trim();
        return normalized.substring(0, Math.min(normalized.length(), 128));
    }

    private String summarize(String message) {
        if (message == null) {
            return null;
        }
        return message.substring(0, Math.min(message.length(), 500));
    }

    private long micros(Duration duration) {
        try {
            return Math.multiplyExact(duration.toMillis(), 1_000L);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Runtime command duration is too large", exception);
        }
    }
}
