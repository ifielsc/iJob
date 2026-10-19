package com.ifielsc.ijob.core.trigger;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.MessageDefinition;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.contract.enums.TriggerStatus;
import com.ifielsc.ijob.contract.enums.TriggerType;
import com.ifielsc.ijob.core.error.CoreMessage;
import com.ifielsc.ijob.core.execute.variable.BusinessTimeSnapshot;
import com.ifielsc.ijob.core.job.definition.JobAdmissionSnapshot;
import com.ifielsc.ijob.core.runtime.RuntimeErrorMetadata;
import com.ifielsc.ijob.core.state.TriggerStateMachine;
import com.ifielsc.ijob.core.task.definition.TaskAdmissionSnapshot;
import com.ifielsc.ijob.infra.coordination.work.DatabaseTriggerWorkQueue;
import com.ifielsc.ijob.infra.coordination.work.ExecutionFenceCoordinator;
import com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit;
import com.ifielsc.ijob.infra.entity.event.EventBinding;
import com.ifielsc.ijob.infra.entity.runtime.Trigger;
import com.ifielsc.ijob.infra.entity.schedule.Schedule;
import com.ifielsc.ijob.infra.mapper.runtime.TriggerMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class TriggerRuntimeService {
    private static final DateTimeFormatter KEY_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,96}");

    private final TriggerMapper triggerMapper;
    private final DatabaseTriggerWorkQueue workQueue;
    private final ExecutionFenceCoordinator fenceCoordinator;

    private final ZoneId businessZone;

    @Autowired
    public TriggerRuntimeService(
            TriggerMapper triggerMapper,
            DatabaseTriggerWorkQueue workQueue,
            ExecutionFenceCoordinator fenceCoordinator,
            AppProperties properties) {
        this.triggerMapper = triggerMapper;
        this.workQueue = workQueue;
        this.fenceCoordinator = fenceCoordinator;
        this.businessZone = ZoneId.of(properties.getSchedule().getZoneId());
    }

    public TriggerRuntimeService(
            TriggerMapper triggerMapper,
            DatabaseTriggerWorkQueue workQueue,
            ExecutionFenceCoordinator fenceCoordinator) {
        this.triggerMapper = triggerMapper;
        this.workQueue = workQueue;
        this.fenceCoordinator = fenceCoordinator;
        this.businessZone = ZoneId.of("Asia/Shanghai");
    }

    protected TriggerRuntimeService(TriggerMapper triggerMapper) {
        this.triggerMapper = triggerMapper;
        this.workQueue = null;
        this.fenceCoordinator = null;
        this.businessZone = ZoneId.of("Asia/Shanghai");
    }

    public Trigger getTrigger(Long id) {
        return triggerMapper.selectById(id);
    }

    public Trigger createTaskTrigger(Schedule schedule, TaskAdmissionSnapshot snapshot) {
        return createTaskTrigger(schedule, snapshot, schedule.getNextFireTime());
    }

    public Trigger createTaskTrigger(
            Schedule schedule, TaskAdmissionSnapshot snapshot, LocalDateTime fireTime) {
        Trigger trigger = buildScheduleTrigger(schedule, fireTime);
        trigger.setIdempotencyKey(scheduleKey(schedule, fireTime));
        applyTaskSnapshot(trigger, snapshot);
        return insert(trigger, expiresAt(schedule, fireTime));
    }

    public Trigger createJobTrigger(Schedule schedule, JobAdmissionSnapshot snapshot) {
        return createJobTrigger(schedule, snapshot, schedule.getNextFireTime());
    }

    public Trigger createJobTrigger(
            Schedule schedule, JobAdmissionSnapshot snapshot, LocalDateTime fireTime) {
        Trigger trigger = buildScheduleTrigger(schedule, fireTime);
        trigger.setIdempotencyKey(scheduleKey(schedule, fireTime));
        applyJobSnapshot(trigger, snapshot);
        return insert(trigger, expiresAt(schedule, fireTime));
    }

    public Trigger createTaskTrigger(
            EventBinding binding, TaskAdmissionSnapshot snapshot, Long eventId) {
        Trigger trigger = buildEventTrigger(binding);
        trigger.setIdempotencyKey(eventKey(binding, eventId));
        trigger.setEventId(eventId);
        trigger.setEventBindingId(binding.getId());
        applyTaskSnapshot(trigger, snapshot);
        return insert(trigger);
    }

    public Trigger createJobTrigger(
            EventBinding binding, JobAdmissionSnapshot snapshot, Long eventId) {
        Trigger trigger = buildEventTrigger(binding);
        trigger.setIdempotencyKey(eventKey(binding, eventId));
        trigger.setEventId(eventId);
        trigger.setEventBindingId(binding.getId());
        applyJobSnapshot(trigger, snapshot);
        return insert(trigger);
    }

    public Trigger createUnavailableEventTrigger(EventBinding binding, Long eventId) {
        Trigger trigger = buildEventTrigger(binding);
        trigger.setIdempotencyKey(eventKey(binding, eventId));
        trigger.setEventId(eventId);
        trigger.setEventBindingId(binding.getId());
        trigger.setStatus(TriggerStatus.SKIPPED);
        trigger.setMessage(CoreMessage.EVENT_TARGET_UNAVAILABLE.defaultMessage());
        RuntimeErrorMetadata metadata =
                RuntimeErrorMetadata.from(CoreMessage.EVENT_TARGET_UNAVAILABLE, new Object[0]);
        trigger.setErrorCode(metadata.errorCode());
        trigger.setMessageKey(metadata.messageKey());
        trigger.setMessageArgs(metadata.messageArgs());
        return insert(trigger);
    }

    public Trigger createManualTaskTrigger(TaskAdmissionSnapshot snapshot) {
        return createManualTaskTrigger(snapshot, null);
    }

    public Trigger createManualTaskTrigger(TaskAdmissionSnapshot snapshot, String variables) {
        return createManualTaskTrigger(snapshot, variables, null);
    }

    public Trigger createManualTaskTrigger(
            TaskAdmissionSnapshot snapshot, String variables, String requestId) {
        Trigger trigger = buildManualTrigger(TargetType.TASK, snapshot.taskId());
        trigger.setIdempotencyKey(manualKey(TargetType.TASK, snapshot.taskId(), requestId));
        trigger.setVariables(variables);
        applyTaskSnapshot(trigger, snapshot);
        return insert(trigger);
    }

    public Trigger createManualJobTrigger(JobAdmissionSnapshot snapshot) {
        return createManualJobTrigger(snapshot, null);
    }

    public Trigger createManualJobTrigger(JobAdmissionSnapshot snapshot, String variables) {
        return createManualJobTrigger(snapshot, variables, null);
    }

    public Trigger createManualJobTrigger(
            JobAdmissionSnapshot snapshot, String variables, String requestId) {
        Trigger trigger = buildManualTrigger(TargetType.JOB, snapshot.jobId());
        trigger.setIdempotencyKey(manualKey(TargetType.JOB, snapshot.jobId(), requestId));
        trigger.setVariables(variables);
        applyJobSnapshot(trigger, snapshot);
        return insert(trigger);
    }

    public boolean claimRunning(Long id) {
        Trigger current = requireTrigger(id);
        if (current.getStatus() != TriggerStatus.WAITING) {
            return false;
        }
        return transition(current, TriggerStatus.RUNNING, null);
    }

    public void markSuccess(Long id) {
        complete(id, TriggerStatus.SUCCESS, null);
    }

    public void markFailed(Long id, String errorMsg) {
        complete(id, TriggerStatus.FAILED, errorMsg);
    }

    public void markStopped(Long id, String message) {
        complete(id, TriggerStatus.STOPPED, message);
    }

    public void markTimedOut(Long id, String message) {
        complete(id, TriggerStatus.TIMED_OUT, message);
    }

    public void markSkipped(Long id, String message) {
        Trigger current = requireTrigger(id);
        if (current.getStatus() == TriggerStatus.SKIPPED) {
            return;
        }
        transition(current, TriggerStatus.SKIPPED, message);
    }

    private Trigger buildScheduleTrigger(Schedule schedule, LocalDateTime fireTime) {
        Trigger trigger = new Trigger();
        trigger.setScheduleId(schedule.getId());
        trigger.setTargetType(schedule.getTargetType());
        trigger.setTargetId(schedule.getTargetId());
        trigger.setTriggerType(TriggerType.SCHEDULE);
        trigger.setStatus(TriggerStatus.WAITING);
        trigger.setTriggerTime(fireTime);
        trigger.setVariables(schedule.getVariables());
        return trigger;
    }

    private Trigger buildEventTrigger(EventBinding binding) {
        Trigger trigger = new Trigger();
        trigger.setTargetType(binding.getTargetType());
        trigger.setTargetId(binding.getTargetId());
        trigger.setTriggerType(TriggerType.EVENT);
        trigger.setEventCode(binding.getEventCode());
        trigger.setStatus(TriggerStatus.WAITING);
        trigger.setTriggerTime(LocalDateTime.now(ZoneOffset.UTC));
        return trigger;
    }

    private Trigger buildManualTrigger(TargetType targetType, Long targetId) {
        Trigger trigger = new Trigger();
        trigger.setTargetType(targetType);
        trigger.setTargetId(targetId);
        trigger.setTriggerType(TriggerType.MANUAL);
        trigger.setStatus(TriggerStatus.WAITING);
        trigger.setTriggerTime(LocalDateTime.now(ZoneOffset.UTC));
        return trigger;
    }

    private void applyTaskSnapshot(Trigger trigger, TaskAdmissionSnapshot snapshot) {
        if (trigger.getTargetType() != TargetType.TASK
                || !trigger.getTargetId().equals(snapshot.taskId())) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "Task trigger snapshot does not match target");
        }
        trigger.setDefinitionId(null);
        trigger.setTaskDefinitionId(snapshot.taskDefinitionId());
    }

    private void applyJobSnapshot(Trigger trigger, JobAdmissionSnapshot snapshot) {
        if (trigger.getTargetType() != TargetType.JOB
                || !trigger.getTargetId().equals(snapshot.jobId())) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "Job trigger snapshot does not match target");
        }
        trigger.setDefinitionId(snapshot.jobDefinitionId());
        trigger.setTaskDefinitionId(null);
    }

    private Trigger insert(Trigger trigger) {
        return insert(trigger, null);
    }

    private Trigger insert(Trigger trigger, LocalDateTime expiresAt) {
        BusinessTimeSnapshot.capture(trigger, businessZone);
        if (workQueue == null || fenceCoordinator == null || !fenceCoordinator.isEnforced()) {
            if (triggerMapper.insert(trigger) != 1
                    || trigger.getId() == null
                    || trigger.getId() <= 0) {
                throw BusinessException.localized(
                        ResultCode.SYSTEM_ERROR, "Trigger creation failed");
            }
            return trigger;
        }

        triggerMapper.insert(trigger);
        Trigger persisted = triggerMapper.selectByIdempotencyKey(trigger.getIdempotencyKey());
        if (persisted == null || persisted.getId() == null || persisted.getId() <= 0) {
            throw BusinessException.localized(ResultCode.SYSTEM_ERROR, "Trigger creation failed");
        }
        requireEquivalent(trigger, persisted);
        com.ifielsc.ijob.core.auth.AuditOutcome.accepted(
                !java.util.Objects.equals(trigger.getId(), persisted.getId()));
        if (!persisted.getStatus().isFinished()) {
            workQueue.enqueue(persisted.getId(), expiresAt);
        }
        return persisted;
    }

    private LocalDateTime expiresAt(Schedule schedule, LocalDateTime fireTime) {
        return schedule.getMaxQueueDelaySec() == null
                ? null
                : fireTime.plusSeconds(schedule.getMaxQueueDelaySec());
    }

    private String scheduleKey(Schedule schedule, LocalDateTime fireTime) {
        return "SCHEDULE:" + schedule.getId() + ":" + fireTime.format(KEY_TIME);
    }

    private String eventKey(EventBinding binding, Long eventId) {
        return "EVENT:" + eventId + ":" + binding.getId();
    }

    private String manualKey(TargetType targetType, Long targetId, String requestId) {
        String resolved =
                requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
        if (!REQUEST_ID.matcher(resolved).matches()) {
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR,
                    "Manual trigger requestId must contain 1 to 96 safe characters");
        }
        return "MANUAL:" + targetType + ":" + targetId + ":" + resolved;
    }

    private void requireEquivalent(Trigger requested, Trigger persisted) {
        if (!Objects.equals(requested.getIdempotencyKey(), persisted.getIdempotencyKey())
                || requested.getTargetType() != persisted.getTargetType()
                || !Objects.equals(requested.getTargetId(), persisted.getTargetId())
                || requested.getTriggerType() != persisted.getTriggerType()
                || !Objects.equals(requested.getScheduleId(), persisted.getScheduleId())
                || !Objects.equals(requested.getEventId(), persisted.getEventId())
                || !Objects.equals(requested.getEventBindingId(), persisted.getEventBindingId())
                || !Objects.equals(requested.getDefinitionId(), persisted.getDefinitionId())
                || !Objects.equals(requested.getTaskDefinitionId(), persisted.getTaskDefinitionId())
                || !Objects.equals(requested.getVariables(), persisted.getVariables())) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "Idempotency key is already used by another trigger");
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public void completeReason(Long id, TriggerStatus status, MessageDefinition reason) {
        Trigger current = requireTrigger(id);
        if (current.getStatus() == status || current.getStatus().isFinished()) return;
        transition(
                current,
                status,
                reason.defaultMessage(),
                RuntimeErrorMetadata.from(reason, new Object[0]));
    }

    private void complete(Long id, TriggerStatus status, String message) {
        Trigger current = requireTrigger(id);
        if (current.getStatus() == status || current.getStatus().isFinished()) {
            return;
        }
        transition(current, status, message);
    }

    private boolean transition(Trigger current, TriggerStatus status, String message) {
        return transition(current, status, message, RuntimeErrorMetadata.forStatus(status));
    }

    @org.springframework.transaction.annotation.Transactional
    public void completeFromRoot(Long id, com.ifielsc.ijob.infra.entity.runtime.Instance root) {
        TriggerStatus status = TriggerStatus.valueOf(root.getStatus().name());
        Trigger current = requireTrigger(id);
        if (current.getStatus().isFinished()) return;
        RuntimeErrorMetadata metadata =
                root.getMessageKey() == null
                        ? RuntimeErrorMetadata.forStatus(status)
                        : new RuntimeErrorMetadata(
                                root.getErrorCode(), root.getMessageKey(), root.getMessageArgs());
        transition(current, status, root.getMessage(), metadata);
    }

    private boolean transition(
            Trigger current, TriggerStatus status, String message, RuntimeErrorMetadata metadata) {
        if (!TriggerStateMachine.canTransition(current.getStatus(), status)) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT,
                    "Illegal trigger state transition: " + current.getStatus() + " -> " + status);
        }
        ExecutionFencePermit permit =
                fenceCoordinator == null
                        ? ExecutionFencePermit.unfenced()
                        : fenceCoordinator.requirePermit(current.getId());
        if (triggerMapper.updateStatusIfCurrent(
                        current.getId(),
                        current.getStatus(),
                        status,
                        message,
                        metadata.errorCode(),
                        metadata.messageKey(),
                        metadata.messageArgs(),
                        permit)
                == 1) {
            return true;
        }
        Trigger latest = requireTrigger(current.getId());
        if (latest.getStatus() == status || latest.getStatus().isFinished()) {
            return false;
        }
        throw BusinessException.localized(
                ResultCode.CONFLICT, "Trigger state changed concurrently");
    }

    private Trigger requireTrigger(Long id) {
        Trigger trigger = triggerMapper.selectById(id);
        if (trigger == null) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Trigger not found");
        }
        return trigger;
    }
}
