package com.ifielsc.ijob.core.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.ExecuteCondition;
import com.ifielsc.ijob.contract.enums.FailStrategy;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.contract.enums.TriggerType;
import com.ifielsc.ijob.contract.runtime.InstanceDTO;
import com.ifielsc.ijob.contract.runtime.InstancePageQuery;
import com.ifielsc.ijob.core.execute.graph.JobExecutionNode;
import com.ifielsc.ijob.core.job.definition.JobAdmissionResolver;
import com.ifielsc.ijob.core.job.definition.JobAdmissionSnapshot;
import com.ifielsc.ijob.core.job.definition.JobRuntimeNestingGuard;
import com.ifielsc.ijob.core.task.definition.TaskAdmissionResolver;
import com.ifielsc.ijob.core.task.definition.TaskAdmissionSnapshot;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;
import com.ifielsc.ijob.infra.entity.runtime.Instance;
import com.ifielsc.ijob.infra.entity.runtime.Trigger;
import com.ifielsc.ijob.infra.mapper.runtime.InstanceMapper;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

class InstanceServiceTest {
    @Test
    void shouldCopyTriggerSnapshotIntoRootInstanceInsert() {
        FakeInstanceMapper mapper = new FakeInstanceMapper();
        InstanceService service = service(mapper);
        Trigger trigger = new Trigger();
        trigger.setId(71L);
        trigger.setTargetType(TargetType.TASK);
        trigger.setTargetId(9L);
        trigger.setTriggerType(TriggerType.MANUAL);
        trigger.setDefinitionId(null);
        trigger.setTaskDefinitionId(41L);
        trigger.setVariables("{\"batch\":7}");

        Long instanceId = service.create(trigger);

        assertEquals(81L, instanceId);
        assertEquals(71L, mapper.inserted.getTriggerId());
        assertEquals(41L, mapper.inserted.getTaskDefinitionId());
        assertNull(mapper.inserted.getDefinitionId());
        assertNull(mapper.inserted.getDefinitionNodeId());
        assertEquals("{\"batch\":7}", mapper.inserted.getInputVariables());
        assertEquals(81L, mapper.updated.getRootInstanceId());
        assertEquals(41L, mapper.updated.getTaskDefinitionId());
        assertEquals("{\"batch\":7}", mapper.updated.getInputVariables());
    }

    @Test
    void shouldCopyJobDefinitionAndKeepTaskSnapshotNull() {
        FakeInstanceMapper mapper = new FakeInstanceMapper();
        InstanceService service = service(mapper);
        Trigger trigger = new Trigger();
        trigger.setId(72L);
        trigger.setTargetType(TargetType.JOB);
        trigger.setTargetId(10L);
        trigger.setTriggerType(TriggerType.EVENT);
        trigger.setDefinitionId(51L);

        service.create(trigger);

        assertEquals(51L, mapper.inserted.getDefinitionId());
        assertNull(mapper.inserted.getTaskDefinitionId());
        assertNull(mapper.inserted.getDefinitionNodeId());
    }

    @Test
    void shouldRejectFailedInsertOrRootInitialization() {
        FakeInstanceMapper insertFailure = new FakeInstanceMapper();
        insertFailure.insertResult = 0;
        BusinessException insertException =
                assertThrows(
                        BusinessException.class,
                        () -> service(insertFailure).create(new Trigger()));

        FakeInstanceMapper updateFailure = new FakeInstanceMapper();
        updateFailure.updateResult = 0;
        BusinessException updateException =
                assertThrows(
                        BusinessException.class,
                        () -> service(updateFailure).create(new Trigger()));

        assertEquals(ResultCode.SYSTEM_ERROR, insertException.getCode());
        assertEquals(ResultCode.SYSTEM_ERROR, updateException.getCode());
    }

    @Test
    void shouldLateBindTaskChildAndWriteAllSnapshots() {
        FakeInstanceMapper mapper = new FakeInstanceMapper();
        mapper.selected = parent();
        RecordingTaskResolver taskResolver = new RecordingTaskResolver();
        InstanceService service = service(mapper, taskResolver, new RecordingJobResolver());
        JobExecutionNode node = node(TargetType.TASK, 9L);

        Long childId = service.createChild(90L, node);

        assertEquals(81L, childId);
        assertNull(taskResolver.taskId);
        assertNull(mapper.inserted.getNodeId());
        assertEquals(61L, mapper.inserted.getDefinitionNodeId());
        assertEquals(51L, mapper.inserted.getDefinitionId());
        assertEquals(41L, mapper.inserted.getTaskDefinitionId());
        assertEquals(71L, mapper.inserted.getTriggerId());
        assertEquals(TriggerType.SCHEDULE, mapper.inserted.getTriggerType());
        assertEquals("{\"batch\":7}", mapper.inserted.getInputVariables());
    }

    @Test
    void shouldLateBindNestedJobDefinition() {
        FakeInstanceMapper mapper = new FakeInstanceMapper();
        mapper.selected = parent();
        RecordingJobResolver jobResolver = new RecordingJobResolver();
        InstanceService service = service(mapper, new RecordingTaskResolver(), jobResolver);

        service.createChild(90L, node(TargetType.JOB, 10L));

        assertNull(jobResolver.jobId);
        assertEquals(71L, mapper.inserted.getDefinitionId());
        assertEquals(61L, mapper.inserted.getDefinitionNodeId());
        assertNull(mapper.inserted.getTaskDefinitionId());
    }

    @Test
    void shouldRejectNestedJobCycleBeforeAdmissionAndInsert() {
        FakeInstanceMapper mapper = new FakeInstanceMapper();
        mapper.selected = parent();
        mapper.selected.setTargetId(10L);
        RecordingJobResolver jobResolver = new RecordingJobResolver();
        InstanceService service = service(mapper, new RecordingTaskResolver(), jobResolver);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service.createChild(90L, node(TargetType.JOB, 10L)));

        assertEquals(ResultCode.CONFLICT, exception.getCode());
        assertNull(jobResolver.jobId);
        assertNull(mapper.inserted);
    }

    @Test
    void shouldExposeConditionalPauseAndResumeForTreeControl() {
        FakeInstanceMapper mapper = new FakeInstanceMapper();
        InstanceService service = service(mapper);

        assertTrue(service.pauseIfRunning(81L));
        assertTrue(service.resumeIfPaused(81L));

        mapper.updateResult = 0;
        assertFalse(service.pauseIfRunning(81L));
        assertFalse(service.resumeIfPaused(81L));
    }

    @Test
    void terminalFailureShouldPersistStableErrorMetadata() {
        FakeInstanceMapper mapper = new FakeInstanceMapper();
        mapper.selected = new Instance();
        mapper.selected.setId(81L);
        mapper.selected.setStatus(InstanceStatus.RUNNING);
        InstanceService service = service(mapper);

        service.fail(81L, "connector rejected request");

        assertEquals(InstanceStatus.FAILED, mapper.updated.getStatus());
        assertEquals("IJOB-RUNTIME-0500", mapper.updated.getErrorCode());
        assertEquals("runtime.execution.failed", mapper.updated.getMessageKey());
        assertEquals("connector rejected request", mapper.updated.getMessage());
        assertNull(mapper.updated.getMessageArgs());
    }

    private static Instance parent() {
        Instance parent = new Instance();
        parent.setId(90L);
        parent.setRootInstanceId(90L);
        parent.setTargetType(TargetType.JOB);
        parent.setTargetId(8L);
        parent.setDefinitionId(51L);
        parent.setTriggerId(71L);
        parent.setTriggerType(TriggerType.SCHEDULE);
        parent.setInputVariables("{\"batch\":7}");
        return parent;
    }

    @Test
    void pluginCompletionPersistsMessageAndMetadataInTheSameConditionalUpdate() {
        FakeInstanceMapper mapper = new FakeInstanceMapper();
        mapper.selected = new Instance();
        mapper.selected.setId(81L);
        mapper.selected.setStatus(InstanceStatus.RUNNING);
        var metadata =
                new com.ifielsc.ijob.core.runtime.RuntimeErrorMetadata(
                        "IJOB-PLUGIN-0003", "result.plugin.api-status", "[503]");
        assertTrue(
                service(mapper)
                        .completeTask(81L, InstanceStatus.FAILED, "HTTP status 503", metadata));
        assertEquals("HTTP status 503", mapper.updated.getMessage());
        assertEquals(metadata.errorCode(), mapper.updated.getErrorCode());
        assertEquals(metadata.messageKey(), mapper.updated.getMessageKey());
        assertEquals(metadata.messageArgs(), mapper.updated.getMessageArgs());
        mapper.selected.setStatus(InstanceStatus.FAILED);
        assertFalse(
                service(mapper).completeTask(81L, InstanceStatus.FAILED, "late result", metadata));
        assertEquals("HTTP status 503", mapper.updated.getMessage());
    }

    @Test
    void successAndStopCompletionKeepPluginDescriptions() {
        for (InstanceStatus status : List.of(InstanceStatus.SUCCESS, InstanceStatus.STOPPED)) {
            FakeInstanceMapper mapper = new FakeInstanceMapper();
            mapper.selected = new Instance();
            mapper.selected.setStatus(InstanceStatus.RUNNING);
            assertTrue(service(mapper).completeTask(81L, status, "plugin detail", null));
            assertEquals("plugin detail", mapper.updated.getMessage());
            assertEquals(status, mapper.updated.getStatus());
        }
    }

    @Test
    void lostCompletionRaceReadsCommittedWinnerInsteadOfRepeatableReadSnapshot() {
        FakeInstanceMapper mapper = new FakeInstanceMapper();
        mapper.selected = new Instance();
        mapper.selected.setStatus(InstanceStatus.RUNNING);
        mapper.current = new Instance();
        mapper.current.setStatus(InstanceStatus.STOPPED);
        mapper.updateResult = 0;

        assertFalse(service(mapper).completeTask(81L, InstanceStatus.STOPPED, null, null));
        assertEquals(InstanceStatus.RUNNING, mapper.selected.getStatus());
        mapper.current.setStatus(InstanceStatus.RUNNING);
        assertThrows(
                BusinessException.class,
                () -> service(mapper).completeTask(81L, InstanceStatus.STOPPED, null, null));
    }

    private static InstanceService service(InstanceMapper mapper) {
        return service(mapper, null, null);
    }

    private static InstanceService service(
            InstanceMapper mapper,
            TaskAdmissionResolver taskAdmissionResolver,
            JobAdmissionResolver jobAdmissionResolver) {
        return new InstanceService(
                mapper,
                taskAdmissionResolver,
                jobAdmissionResolver,
                new JobRuntimeNestingGuard(mapper));
    }

    private static JobExecutionNode node(TargetType type, Long refId) {
        return new JobExecutionNode(
                61L,
                51L,
                "node-61",
                "Node 61",
                type,
                refId,
                type == TargetType.JOB ? 71L : null,
                type == TargetType.TASK ? 41L : null,
                ExecuteCondition.ALL_SUCCESS,
                FailStrategy.STOP);
    }

    private static TaskAdmissionSnapshot taskSnapshot() {
        TaskExecutionSpec spec =
                new TaskExecutionSpec("task", PluginType.SCRIPT, "config", 0, 0, null, true, null);
        return new TaskAdmissionSnapshot(9L, 41L, 3L, spec);
    }

    private static final class RecordingTaskResolver extends TaskAdmissionResolver {
        private Long taskId;

        private RecordingTaskResolver() {
            super(null, null);
        }

        @Override
        public TaskAdmissionSnapshot resolve(Long id) {
            taskId = id;
            return taskSnapshot();
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
            return new JobAdmissionSnapshot(id, 71L, 4L, "nested", 60);
        }
    }

    private static final class FakeInstanceMapper implements InstanceMapper {
        @Override
        public List<Instance> selectBoundedVariableTreeByRootId(Long id) {
            return selectTreeByRootId(id);
        }

        public Instance selectSummaryById(Long id) {
            return selectById(id);
        }

        public List<Instance> selectSummaryTreeByRootId(Long id) {
            return selectTreeByRootId(id);
        }

        public Long selectRootIdByTriggerId(Long id) {
            return null;
        }

        @Override
        public int countActiveByTaskId(Long taskId) {
            return 0;
        }

        @Override
        public int countActiveByPluginType(com.ifielsc.ijob.contract.enums.PluginType type) {
            return 0;
        }

        @Override
        public int countActiveByConcurrencyResourceId(Long resourceId) {
            return 0;
        }

        private int insertResult = 1;
        private int updateResult = 1;
        private Instance inserted;
        private Instance updated;
        private Instance selected;
        private Instance current;

        @Override
        public List<Instance> selectByParentInstanceId(Long id) {
            return List.of();
        }

        @Override
        public Instance selectCurrentById(Long id) {
            return current == null ? selectById(id) : current;
        }

        @Override
        public Instance selectById(Long id) {
            return selected;
        }

        @Override
        public Instance selectRootByTriggerId(Long triggerId) {
            return null;
        }

        @Override
        public List<InstanceDTO> selectDtoList(InstancePageQuery query) {
            return List.of();
        }

        @Override
        public List<Instance> selectTreeByRootId(Long rootInstanceId) {
            return List.of();
        }

        @Override
        public int insert(
                Instance instance,
                com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            inserted = instance;
            if (insertResult == 1) {
                instance.setId(81L);
            }
            return insertResult;
        }

        @Override
        public int updateById(
                Instance instance,
                com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            updated = instance;
            return updateResult;
        }

        @Override
        public int markRunningIfWaiting(
                Long id,
                LocalDateTime startTime,
                com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            return updateResult;
        }

        @Override
        public int markWaitingPermitIfWaiting(
                Long id,
                String message,
                LocalDateTime deadline,
                com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            return updateResult;
        }

        @Override
        public int markPausedIfRunning(
                Long id, com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            return updateResult;
        }

        @Override
        public int markRunningIfPaused(
                Long id, com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            return updateResult;
        }

        @Override
        public int markWaitingEventIfRunning(
                Long id, com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            return updateResult;
        }

        @Override
        public int resetWaitingEventForContinuation(
                Long id, com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            return updateResult;
        }

        @Override
        public int updateProgressIfRunning(
                Long id,
                Integer progress,
                com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            return updateResult;
        }

        @Override
        public int updateInputVariablesIfWaiting(
                Long id,
                String inputVariables,
                com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            return updateResult;
        }

        @Override
        public int updateOutputVariablesIfRunning(
                Long id,
                String outputVariables,
                com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            return updateResult;
        }

        @Override
        public int completeIfCurrent(
                Instance instance,
                InstanceStatus expectedStatus,
                com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            updated = instance;
            return updateResult;
        }

        @Override
        public int resetForRetry(
                Long id,
                InstanceStatus expectedStatus,
                com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            return updateResult;
        }

        @Override
        public int adoptActiveTree(Long rootInstanceId, long fenceToken, String claimToken) {
            return updateResult;
        }

        @Override
        public int adoptTreeForRetry(Long rootInstanceId, long fenceToken, String claimToken) {
            return updateResult;
        }
    }
}
