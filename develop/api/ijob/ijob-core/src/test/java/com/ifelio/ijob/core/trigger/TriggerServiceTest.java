package com.ifelio.ijob.core.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.contract.enums.ScheduleMisfirePolicy;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.core.job.definition.JobAdmissionResolver;
import com.ifelio.ijob.core.job.definition.JobAdmissionSnapshot;
import com.ifelio.ijob.core.schedule.ScheduleFirePlan;
import com.ifelio.ijob.core.schedule.ScheduleService;
import com.ifelio.ijob.core.task.definition.TaskAdmissionResolver;
import com.ifelio.ijob.core.task.definition.TaskAdmissionSnapshot;
import com.ifelio.ijob.core.task.definition.TaskExecutionSpec;
import com.ifelio.ijob.infra.entity.event.EventBinding;
import com.ifelio.ijob.infra.entity.runtime.Trigger;
import com.ifelio.ijob.infra.entity.schedule.Schedule;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

class TriggerServiceTest {
    @Test
    void eventTriggerShouldRequirePersistedEventId() {
        TriggerService service =
                new TriggerService(
                        new RecordingRuntimeService(),
                        new RecordingScheduleService(),
                        new RecordingResolver(),
                        new RecordingJobResolver(),
                        new RecordingDispatcher());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.trigger(eventBinding(TargetType.TASK), null));
    }

    @Test
    void shouldResolveTaskEventAndDispatchSnapshotTrigger() {
        RecordingRuntimeService runtime = new RecordingRuntimeService();
        RecordingResolver resolver = new RecordingResolver();
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        TriggerService service =
                new TriggerService(
                        runtime,
                        new RecordingScheduleService(),
                        resolver,
                        new RecordingJobResolver(),
                        dispatcher);
        EventBinding binding = eventBinding(TargetType.TASK);

        service.trigger(binding, 101L);

        assertEquals(9L, resolver.taskId);
        assertEquals(41L, runtime.taskSnapshot.taskDefinitionId());
        assertEquals(101L, runtime.eventId);
        assertEquals(72L, dispatcher.triggerId);
    }

    @Test
    void shouldResolveJobEventAndDispatchSnapshotTrigger() {
        RecordingRuntimeService runtime = new RecordingRuntimeService();
        RecordingResolver resolver = new RecordingResolver();
        RecordingJobResolver jobResolver = new RecordingJobResolver();
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        TriggerService service =
                new TriggerService(
                        runtime, new RecordingScheduleService(), resolver, jobResolver, dispatcher);

        service.trigger(eventBinding(TargetType.JOB), 102L);

        assertEquals(9L, jobResolver.jobId);
        assertEquals(51L, runtime.jobSnapshot.jobDefinitionId());
        assertEquals(102L, runtime.eventId);
        assertEquals(73L, dispatcher.triggerId);
    }

    @Test
    void shouldResolveDueTaskScheduleBeforeAdvancingFireTime() {
        RecordingRuntimeService runtime = new RecordingRuntimeService();
        RecordingResolver resolver = new RecordingResolver();
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        RecordingScheduleService schedules = new RecordingScheduleService();
        TriggerService service =
                new TriggerService(
                        runtime, schedules, resolver, new RecordingJobResolver(), dispatcher);
        Schedule schedule = new Schedule();
        schedule.setId(6L);
        schedule.setTargetType(TargetType.TASK);
        schedule.setTargetId(9L);
        schedule.setEnabled(true);
        schedule.setNextFireTime(LocalDateTime.now().minusSeconds(1));
        schedule.setUpdateTime(LocalDateTime.now().minusMinutes(1));
        schedules.schedule = schedule;

        service.trigger(6L);

        assertEquals(9L, resolver.taskId);
        assertEquals(41L, runtime.taskSnapshot.taskDefinitionId());
        assertEquals(schedule, schedules.markedSchedule);
        assertEquals(74L, dispatcher.triggerId);
    }

    private static EventBinding eventBinding(TargetType targetType) {
        EventBinding binding = new EventBinding();
        binding.setId(5L);
        binding.setTargetType(targetType);
        binding.setTargetId(9L);
        binding.setEventCode("ready");
        binding.setEnabled(true);
        return binding;
    }

    private static TaskAdmissionSnapshot snapshot() {
        TaskExecutionSpec spec =
                new TaskExecutionSpec("task", PluginType.SCRIPT, "config", 0, 0, null, true, null);
        return new TaskAdmissionSnapshot(9L, 41L, 3L, spec);
    }

    private static JobAdmissionSnapshot jobSnapshot() {
        return new JobAdmissionSnapshot(9L, 51L, 4L, "job", 60);
    }

    private static final class RecordingResolver extends TaskAdmissionResolver {
        private Long taskId;

        private RecordingResolver() {
            super(null, null);
        }

        @Override
        public TaskAdmissionSnapshot resolve(Long id) {
            taskId = id;
            return snapshot();
        }
    }

    private static final class RecordingJobResolver extends JobAdmissionResolver {
        private Long jobId;

        private RecordingJobResolver() {
            super(null, null);
        }

        @Override
        public JobAdmissionSnapshot resolve(Long id) {
            jobId = id;
            return jobSnapshot();
        }
    }

    private static final class RecordingRuntimeService extends TriggerRuntimeService {
        private TaskAdmissionSnapshot taskSnapshot;
        private JobAdmissionSnapshot jobSnapshot;
        private Long eventId;

        private RecordingRuntimeService() {
            super(null);
        }

        @Override
        public Trigger createTaskTrigger(
                EventBinding binding, TaskAdmissionSnapshot snapshot, Long selectedEventId) {
            taskSnapshot = snapshot;
            eventId = selectedEventId;
            return trigger(72L);
        }

        @Override
        public Trigger createJobTrigger(
                EventBinding binding, JobAdmissionSnapshot snapshot, Long selectedEventId) {
            jobSnapshot = snapshot;
            eventId = selectedEventId;
            return trigger(73L);
        }

        @Override
        public Trigger createTaskTrigger(Schedule schedule, TaskAdmissionSnapshot snapshot) {
            taskSnapshot = snapshot;
            return trigger(74L);
        }

        @Override
        public Trigger createTaskTrigger(
                Schedule schedule, TaskAdmissionSnapshot snapshot, LocalDateTime fireTime) {
            return createTaskTrigger(schedule, snapshot);
        }

        private Trigger trigger(Long id) {
            Trigger trigger = new Trigger();
            trigger.setId(id);
            trigger.setStatus(com.ifelio.ijob.contract.enums.TriggerStatus.WAITING);
            return trigger;
        }
    }

    private static final class RecordingScheduleService extends ScheduleService {
        private Schedule markedSchedule;
        private Schedule schedule;

        private RecordingScheduleService() {
            super(null, null, null, null, null);
        }

        @Override
        public Schedule claimDueSchedule(Long scheduleId, String fireClaimToken) {
            return schedule;
        }

        @Override
        public void markClaimedFireProcessed(
                Schedule schedule, LocalDateTime expectedNextFireTime, String fireClaimToken) {
            markedSchedule = schedule;
        }

        @Override
        public ScheduleFirePlan planClaimedFire(Schedule schedule, int triggerBudget) {
            return new ScheduleFirePlan(
                    ScheduleMisfirePolicy.FIRE_ONCE,
                    false,
                    schedule.getNextFireTime(),
                    schedule.getNextFireTime(),
                    java.util.List.of(schedule.getNextFireTime()),
                    null,
                    1,
                    0,
                    false);
        }

        @Override
        public void completeClaimedFire(
                Schedule schedule,
                LocalDateTime expectedNextFireTime,
                String fireClaimToken,
                ScheduleFirePlan plan) {
            markedSchedule = schedule;
        }
    }

    private static final class RecordingDispatcher extends TriggerCommandDispatcher {
        private Long triggerId;

        private RecordingDispatcher() {
            super(null);
        }

        @Override
        public void dispatchAfterCommit(Long id) {
            triggerId = id;
        }
    }
}
