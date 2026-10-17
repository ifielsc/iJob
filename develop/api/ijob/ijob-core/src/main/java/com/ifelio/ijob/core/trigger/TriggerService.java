package com.ifelio.ijob.core.trigger;

import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.core.job.definition.JobAdmissionResolver;
import com.ifelio.ijob.core.job.definition.JobAdmissionSnapshot;
import com.ifelio.ijob.core.schedule.ScheduleService;
import com.ifelio.ijob.core.task.definition.TaskAdmissionResolver;
import com.ifelio.ijob.core.task.definition.TaskAdmissionSnapshot;
import com.ifelio.ijob.infra.entity.event.EventBinding;
import com.ifelio.ijob.infra.entity.runtime.Trigger;
import com.ifelio.ijob.infra.entity.schedule.Schedule;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class TriggerService {
    private final TriggerRuntimeService triggerRuntimeService;
    private final ScheduleService scheduleService;
    private final TaskAdmissionResolver taskAdmissionResolver;
    private final JobAdmissionResolver jobAdmissionResolver;
    private final TriggerCommandDispatcher triggerCommandDispatcher;

    @Autowired
    public TriggerService(
            TriggerRuntimeService triggerRuntimeService,
            ScheduleService scheduleService,
            TaskAdmissionResolver taskAdmissionResolver,
            JobAdmissionResolver jobAdmissionResolver,
            TriggerCommandDispatcher triggerCommandDispatcher) {
        this.triggerRuntimeService = triggerRuntimeService;
        this.scheduleService = scheduleService;
        this.taskAdmissionResolver = taskAdmissionResolver;
        this.jobAdmissionResolver = jobAdmissionResolver;
        this.triggerCommandDispatcher = triggerCommandDispatcher;
    }

    @Transactional
    public void trigger(Long scheduleId) {
        trigger(scheduleId, 1);
    }

    @Transactional
    public int trigger(Long scheduleId, int triggerBudget) {
        String fireClaimToken = UUID.randomUUID().toString();
        Schedule schedule = scheduleService.claimDueSchedule(scheduleId, fireClaimToken);
        if (schedule == null) {
            return 0;
        }

        LocalDateTime fireTime = schedule.getNextFireTime();
        if (fireTime == null) {
            throw new IllegalStateException("Claimed schedule has no fire time");
        }

        var plan = scheduleService.planClaimedFire(schedule, triggerBudget);
        List<Trigger> created = new ArrayList<>(plan.fireTimes().size());
        for (LocalDateTime dueTime : plan.fireTimes()) {
            created.add(createTrigger(schedule, dueTime));
        }
        scheduleService.completeClaimedFire(schedule, fireTime, fireClaimToken, plan);

        for (Trigger trigger : created) {
            log.info(
                    "trigger created, triggerId={}, scheduleId={}, targetType={}, targetId={},"
                            + " fireTime={}",
                    trigger.getId(),
                    schedule.getId(),
                    schedule.getTargetType(),
                    schedule.getTargetId(),
                    trigger.getTriggerTime());
            triggerCommandDispatcher.dispatchAfterCommit(trigger.getId());
        }
        return created.size();
    }

    @Transactional
    public Long trigger(EventBinding binding, Long eventId) {
        if (binding == null) {
            return null;
        }
        if (eventId == null) {
            throw new IllegalArgumentException("Persisted event id is required");
        }

        if (!Boolean.TRUE.equals(binding.getEnabled())) {
            return null;
        }

        Trigger trigger = createTrigger(binding, eventId);

        if (!trigger.getStatus().isFinished()) {
            triggerCommandDispatcher.dispatchAfterCommit(trigger.getId());
        }
        return trigger.getId();
    }

    private Trigger createTrigger(Schedule schedule, LocalDateTime fireTime) {
        if (schedule.getTargetType() == TargetType.TASK) {
            TaskAdmissionSnapshot snapshot = taskAdmissionResolver.resolve(schedule.getTargetId());
            return triggerRuntimeService.createTaskTrigger(schedule, snapshot, fireTime);
        }
        JobAdmissionSnapshot snapshot = jobAdmissionResolver.resolve(schedule.getTargetId());
        return triggerRuntimeService.createJobTrigger(schedule, snapshot, fireTime);
    }

    private Trigger createTrigger(EventBinding binding, Long eventId) {
        if (binding.getTargetType() == TargetType.TASK) {
            TaskAdmissionSnapshot snapshot =
                    taskAdmissionResolver.resolveForEvent(binding.getTargetId());
            if (snapshot == null)
                return triggerRuntimeService.createUnavailableEventTrigger(binding, eventId);
            return triggerRuntimeService.createTaskTrigger(binding, snapshot, eventId);
        }
        JobAdmissionSnapshot snapshot = jobAdmissionResolver.resolveForEvent(binding.getTargetId());
        if (snapshot == null)
            return triggerRuntimeService.createUnavailableEventTrigger(binding, eventId);
        return triggerRuntimeService.createJobTrigger(binding, snapshot, eventId);
    }
}
