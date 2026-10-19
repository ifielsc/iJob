package com.ifielsc.ijob.core.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.contract.enums.TriggerStatus;
import com.ifielsc.ijob.contract.enums.TriggerType;
import com.ifielsc.ijob.core.job.definition.JobAdmissionSnapshot;
import com.ifielsc.ijob.core.task.definition.TaskAdmissionSnapshot;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;
import com.ifielsc.ijob.infra.coordination.work.DatabaseTriggerWorkQueue;
import com.ifielsc.ijob.infra.coordination.work.ExecutionFenceCoordinator;
import com.ifielsc.ijob.infra.entity.event.EventBinding;
import com.ifielsc.ijob.infra.entity.runtime.Trigger;
import com.ifielsc.ijob.infra.mapper.runtime.TriggerMapper;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

class TriggerRuntimeServiceTest {
    @Test
    void scheduledAndManualTriggersCaptureConfiguredBusinessZone() {
        var properties = new AppProperties();
        properties.getSchedule().setZoneId("America/New_York");
        var service = new TriggerRuntimeService(new FakeTriggerMapper(), null, null, properties);
        var schedule = new com.ifielsc.ijob.infra.entity.schedule.Schedule();
        schedule.setId(9L);
        schedule.setTargetType(TargetType.TASK);
        schedule.setTargetId(snapshot().taskId());
        var fire = java.time.LocalDateTime.parse("2026-11-01T06:30:00");
        Trigger trigger = service.createTaskTrigger(schedule, snapshot(), fire);
        assertEquals(fire, trigger.getTriggerTime());
        assertEquals("2026-11-01T01:30:00-05:00", trigger.getBusinessTime());
        Trigger manual = service.createManualTaskTrigger(snapshot());
        assertEquals("America/New_York", manual.getBusinessZoneId());
        assertEquals(
                manual.getTriggerTime().toInstant(java.time.ZoneOffset.UTC),
                java.time.OffsetDateTime.parse(manual.getBusinessTime()).toInstant());
    }

    @Test
    void rootFailureMetadataSurvivesTriggerCompletion() {
        var mapper = new FakeTriggerMapper();
        var service = new TriggerRuntimeService(mapper);
        var trigger = service.createManualTaskTrigger(snapshot());
        mapper.selected = trigger;
        assertTrue(service.claimRunning(trigger.getId()));
        var root = new com.ifielsc.ijob.infra.entity.runtime.Instance();
        root.setStatus(com.ifielsc.ijob.contract.enums.InstanceStatus.FAILED);
        root.setMessage("Required variable is missing: inputId");
        root.setErrorCode("IJOB-DETAIL-MISSING-VARIABLE");
        root.setMessageKey("error.detail.missing-variable");
        root.setMessageArgs("[\"inputId\"]");
        service.completeFromRoot(trigger.getId(), root);
        assertEquals(TriggerStatus.FAILED, trigger.getStatus());
        assertEquals(root.getErrorCode(), trigger.getErrorCode());
        assertEquals(root.getMessageKey(), trigger.getMessageKey());
        assertEquals(root.getMessageArgs(), trigger.getMessageArgs());
    }

    @Test
    void developmentModeShouldCreateTriggerWithoutWritingPersistentWorkQueue() {
        AppProperties properties = new AppProperties();
        properties.getRuntime().setMode(AppProperties.RuntimeMode.DEVELOPMENT);
        FakeTriggerMapper mapper = new FakeTriggerMapper();
        TriggerRuntimeService service =
                new TriggerRuntimeService(
                        mapper,
                        new DatabaseTriggerWorkQueue(null, properties),
                        new ExecutionFenceCoordinator(properties));

        Trigger trigger = service.createManualTaskTrigger(snapshot());

        assertEquals(71L, trigger.getId());
        assertEquals(trigger, mapper.inserted);
    }

    @Test
    void shouldInsertManualJobTriggerWithImmutableSnapshot() {
        FakeTriggerMapper mapper = new FakeTriggerMapper();
        TriggerRuntimeService service = new TriggerRuntimeService(mapper);

        Trigger trigger =
                service.createManualJobTrigger(new JobAdmissionSnapshot(8L, 31L, 2L, "job", 60));

        assertEquals(TargetType.JOB, trigger.getTargetType());
        assertEquals(8L, trigger.getTargetId());
        assertEquals(TriggerType.MANUAL, trigger.getTriggerType());
        assertEquals(TriggerStatus.WAITING, trigger.getStatus());
        assertEquals(31L, trigger.getDefinitionId());
        assertNull(trigger.getTaskDefinitionId());
        assertTrue(trigger.getIdempotencyKey().startsWith("MANUAL:JOB:8:"));
    }

    @Test
    void shouldInsertJobSnapshotWithEventTriggerFields() {
        FakeTriggerMapper mapper = new FakeTriggerMapper();
        TriggerRuntimeService service = new TriggerRuntimeService(mapper);
        EventBinding binding = new EventBinding();
        binding.setId(51L);
        binding.setTargetType(TargetType.JOB);
        binding.setTargetId(8L);
        binding.setEventCode("ready");

        Trigger trigger =
                service.createJobTrigger(
                        binding, new JobAdmissionSnapshot(8L, 31L, 2L, "job", 60), 91L);

        assertEquals(71L, trigger.getId());
        assertEquals(TargetType.JOB, trigger.getTargetType());
        assertEquals(8L, trigger.getTargetId());
        assertEquals(31L, trigger.getDefinitionId());
        assertNull(trigger.getTaskDefinitionId());
        assertEquals(91L, trigger.getEventId());
        assertEquals(51L, trigger.getEventBindingId());
        assertEquals("EVENT:91:51", trigger.getIdempotencyKey());
        assertEquals(trigger, mapper.inserted);
    }

    @Test
    void shouldInsertTaskSnapshotWithEventTriggerFields() {
        FakeTriggerMapper mapper = new FakeTriggerMapper();
        TriggerRuntimeService service = new TriggerRuntimeService(mapper);
        EventBinding binding = new EventBinding();
        binding.setId(52L);
        binding.setTargetType(TargetType.TASK);
        binding.setTargetId(9L);
        binding.setEventCode("ready");

        Trigger trigger = service.createTaskTrigger(binding, snapshot(), 92L);

        assertEquals(71L, trigger.getId());
        assertEquals(TargetType.TASK, trigger.getTargetType());
        assertEquals(9L, trigger.getTargetId());
        assertEquals(TriggerType.EVENT, trigger.getTriggerType());
        assertEquals(TriggerStatus.WAITING, trigger.getStatus());
        assertEquals(41L, trigger.getTaskDefinitionId());
        assertNull(trigger.getDefinitionId());
        assertEquals(92L, trigger.getEventId());
        assertEquals(52L, trigger.getEventBindingId());
        assertEquals("EVENT:92:52", trigger.getIdempotencyKey());
        assertEquals(trigger, mapper.inserted);
    }

    @Test
    void shouldRejectTargetMismatchOrFailedInsert() {
        TriggerRuntimeService mismatchService = new TriggerRuntimeService(new FakeTriggerMapper());
        EventBinding mismatched = new EventBinding();
        mismatched.setTargetType(TargetType.TASK);
        mismatched.setTargetId(10L);
        BusinessException mismatch =
                assertThrows(
                        BusinessException.class,
                        () -> mismatchService.createTaskTrigger(mismatched, snapshot(), 93L));

        FakeTriggerMapper failedMapper = new FakeTriggerMapper();
        failedMapper.insertResult = 0;
        TriggerRuntimeService failedService = new TriggerRuntimeService(failedMapper);
        BusinessException failed =
                assertThrows(
                        BusinessException.class,
                        () -> failedService.createManualTaskTrigger(snapshot()));

        assertEquals(ResultCode.SYSTEM_ERROR, mismatch.getCode());
        assertEquals(ResultCode.SYSTEM_ERROR, failed.getCode());
    }

    @Test
    void shouldClaimOnceAndKeepFirstTerminalState() {
        FakeTriggerMapper mapper = new FakeTriggerMapper();
        TriggerRuntimeService service = new TriggerRuntimeService(mapper);
        Trigger trigger = service.createManualTaskTrigger(snapshot());
        mapper.selected = trigger;

        assertTrue(service.claimRunning(trigger.getId()));
        assertFalse(service.claimRunning(trigger.getId()));

        service.markTimedOut(trigger.getId(), "Task timeout");
        service.markSuccess(trigger.getId());

        assertEquals(TriggerStatus.TIMED_OUT, trigger.getStatus());
        assertEquals("Task timeout", trigger.getMessage());
        assertEquals("IJOB-RUNTIME-0504", trigger.getErrorCode());
        assertEquals("runtime.execution.timed-out", trigger.getMessageKey());
    }

    @Test
    void shouldFailWaitingTriggerRejectedBeforeClaim() {
        FakeTriggerMapper mapper = new FakeTriggerMapper();
        TriggerRuntimeService service = new TriggerRuntimeService(mapper);
        Trigger trigger = service.createManualTaskTrigger(snapshot());
        mapper.selected = trigger;

        service.markFailed(trigger.getId(), "Trigger execution rejected by capacity limit");

        assertEquals(TriggerStatus.FAILED, trigger.getStatus());
        assertEquals("Trigger execution rejected by capacity limit", trigger.getMessage());
        assertEquals("IJOB-RUNTIME-0500", trigger.getErrorCode());
        assertEquals("runtime.execution.failed", trigger.getMessageKey());
        assertFalse(service.claimRunning(trigger.getId()));
    }

    @Test
    void concurrentClaimShouldHaveSingleWinner() {
        ConcurrentTriggerMapper mapper =
                new ConcurrentTriggerMapper(trigger(TriggerStatus.WAITING));
        TriggerRuntimeService service = new TriggerRuntimeService(mapper);
        mapper.prepareConcurrentReads();

        List<Boolean> results = runConcurrentClaims(service, 71L);

        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(TriggerStatus.RUNNING, mapper.status());
        assertEquals(1, mapper.successfulUpdates());
    }

    @Test
    void concurrentTerminalUpdatesShouldKeepFirstWinner() {
        ConcurrentTriggerMapper mapper =
                new ConcurrentTriggerMapper(trigger(TriggerStatus.RUNNING));
        TriggerRuntimeService service = new TriggerRuntimeService(mapper);
        mapper.prepareConcurrentReads();

        runConcurrently(() -> service.markSuccess(71L), () -> service.markTimedOut(71L, "timeout"));

        assertTrue(
                mapper.status() == TriggerStatus.SUCCESS
                        || mapper.status() == TriggerStatus.TIMED_OUT);
        assertEquals(1, mapper.successfulUpdates());
    }

    private static TaskAdmissionSnapshot snapshot() {
        TaskExecutionSpec spec =
                new TaskExecutionSpec("task", PluginType.SCRIPT, "config", 0, 0, null, true, null);
        return new TaskAdmissionSnapshot(9L, 41L, 3L, spec);
    }

    private static Trigger trigger(TriggerStatus status) {
        Trigger trigger = new Trigger();
        trigger.setId(71L);
        trigger.setStatus(status);
        return trigger;
    }

    private static List<Boolean> runConcurrentClaims(
            TriggerRuntimeService service, Long triggerId) {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> service.claimRunning(triggerId));
            Future<Boolean> second = executor.submit(() -> service.claimRunning(triggerId));
            return List.of(first.get(), second.get());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        } catch (ExecutionException exception) {
            throw new AssertionError(exception.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    private static void runConcurrently(Runnable firstAction, Runnable secondAction) {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(firstAction);
            Future<?> second = executor.submit(secondAction);
            first.get();
            second.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        } catch (ExecutionException exception) {
            throw new AssertionError(exception.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class ConcurrentTriggerMapper implements TriggerMapper {
        private final Trigger stored;
        private final AtomicInteger successfulUpdates = new AtomicInteger();
        private AtomicInteger gatedReads = new AtomicInteger();
        private CountDownLatch concurrentReads = new CountDownLatch(0);

        private ConcurrentTriggerMapper(Trigger stored) {
            this.stored = stored;
        }

        private void prepareConcurrentReads() {
            gatedReads = new AtomicInteger(2);
            concurrentReads = new CountDownLatch(2);
        }

        private TriggerStatus status() {
            synchronized (stored) {
                return stored.getStatus();
            }
        }

        private int successfulUpdates() {
            return successfulUpdates.get();
        }

        @Override
        public Trigger selectById(Long id) {
            Trigger snapshot;
            synchronized (stored) {
                snapshot = trigger(stored.getStatus());
                snapshot.setMessage(stored.getMessage());
            }
            if (gatedReads.getAndDecrement() > 0) {
                concurrentReads.countDown();
                try {
                    concurrentReads.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
            }
            return snapshot;
        }

        @Override
        public Trigger selectByIdempotencyKey(String idempotencyKey) {
            return null;
        }

        @Override
        public java.util.List<com.ifielsc.ijob.contract.runtime.TriggerDTO> selectDtoList(
                com.ifielsc.ijob.contract.runtime.TriggerPageQuery query) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<Long> selectWaitingIds(int limit) {
            return java.util.List.of();
        }

        @Override
        public int insert(Trigger trigger) {
            return 0;
        }

        @Override
        public int updateStatusIfCurrent(
                Long id,
                TriggerStatus expectedStatus,
                TriggerStatus status,
                String message,
                String errorCode,
                String messageKey,
                String messageArgs,
                com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            synchronized (stored) {
                if (stored.getStatus() != expectedStatus) {
                    return 0;
                }
                stored.setStatus(status);
                stored.setMessage(message);
                stored.setErrorCode(errorCode);
                stored.setMessageKey(messageKey);
                stored.setMessageArgs(messageArgs);
                successfulUpdates.incrementAndGet();
                return 1;
            }
        }
    }

    private static final class FakeTriggerMapper implements TriggerMapper {
        private int insertResult = 1;
        private Trigger inserted;
        private Trigger selected;

        @Override
        public Trigger selectById(Long id) {
            return selected;
        }

        @Override
        public Trigger selectByIdempotencyKey(String idempotencyKey) {
            return selected;
        }

        @Override
        public java.util.List<com.ifielsc.ijob.contract.runtime.TriggerDTO> selectDtoList(
                com.ifielsc.ijob.contract.runtime.TriggerPageQuery query) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<Long> selectWaitingIds(int limit) {
            return java.util.List.of();
        }

        @Override
        public int insert(Trigger trigger) {
            inserted = trigger;
            if (insertResult == 1) {
                trigger.setId(71L);
            }
            return insertResult;
        }

        @Override
        public int updateStatusIfCurrent(
                Long id,
                TriggerStatus expectedStatus,
                TriggerStatus status,
                String message,
                String errorCode,
                String messageKey,
                String messageArgs,
                com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            if (selected == null || selected.getStatus() != expectedStatus) {
                return 0;
            }
            selected.setStatus(status);
            selected.setMessage(message);
            selected.setErrorCode(errorCode);
            selected.setMessageKey(messageKey);
            selected.setMessageArgs(messageArgs);
            return 1;
        }
    }
}
