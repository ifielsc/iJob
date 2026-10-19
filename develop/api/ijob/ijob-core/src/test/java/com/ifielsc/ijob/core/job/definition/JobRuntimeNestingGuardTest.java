package com.ifielsc.ijob.core.job.definition;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.contract.runtime.InstanceDTO;
import com.ifielsc.ijob.contract.runtime.InstancePageQuery;
import com.ifielsc.ijob.infra.entity.runtime.Instance;
import com.ifielsc.ijob.infra.mapper.runtime.InstanceMapper;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class JobRuntimeNestingGuardTest {
    @Test
    void shouldAcceptEightLevelsAndRejectNinthChild() {
        RecordingMapper mapper = chain(8);
        JobRuntimeNestingGuard guard = new JobRuntimeNestingGuard(mapper);

        assertDoesNotThrow(() -> guard.validateInstance(mapper.instances.get(8L)));
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> guard.validateChild(mapper.instances.get(8L), 9L));

        assertEquals(ResultCode.CONFLICT, exception.getCode());
        assertEquals("Nested job runtime depth exceeds 8 levels", exception.getMessage());
    }

    @Test
    void shouldAcceptChildThatFormsEighthLevel() {
        RecordingMapper mapper = chain(7);
        JobRuntimeNestingGuard guard = new JobRuntimeNestingGuard(mapper);

        assertDoesNotThrow(() -> guard.validateChild(mapper.instances.get(7L), 8L));
    }

    @Test
    void shouldRejectCycleAcrossMixedRuntimeSnapshots() {
        RecordingMapper mapper = chain(2);
        JobRuntimeNestingGuard guard = new JobRuntimeNestingGuard(mapper);

        BusinessException candidateCycle =
                assertThrows(
                        BusinessException.class,
                        () -> guard.validateChild(mapper.instances.get(2L), 1L));

        Instance repeated = instance(3L, 1L, 2L, 1L);
        mapper.instances.put(3L, repeated);
        BusinessException existingCycle =
                assertThrows(BusinessException.class, () -> guard.validateInstance(repeated));

        assertEquals(ResultCode.CONFLICT, candidateCycle.getCode());
        assertEquals(ResultCode.CONFLICT, existingCycle.getCode());
        assertEquals("Nested job runtime cycle detected", existingCycle.getMessage());
    }

    @Test
    void shouldRejectBrokenPersistedAncestry() {
        RecordingMapper mapper = new RecordingMapper();
        Instance leaf = instance(2L, 2L, 99L, 1L);
        mapper.instances.put(2L, leaf);
        JobRuntimeNestingGuard guard = new JobRuntimeNestingGuard(mapper);

        BusinessException exception =
                assertThrows(BusinessException.class, () -> guard.validateInstance(leaf));

        assertEquals(ResultCode.SYSTEM_ERROR, exception.getCode());
        assertEquals("Job runtime instance ancestry is invalid", exception.getMessage());
    }

    private static RecordingMapper chain(int depth) {
        RecordingMapper mapper = new RecordingMapper();
        for (long id = 1; id <= depth; id++) {
            mapper.instances.put(id, instance(id, id, id == 1 ? null : id - 1, 1L));
        }
        return mapper;
    }

    private static Instance instance(Long id, Long jobId, Long parentId, Long rootId) {
        Instance instance = new Instance();
        instance.setId(id);
        instance.setTargetType(TargetType.JOB);
        instance.setTargetId(jobId);
        instance.setDefinitionId(100L + id);
        instance.setParentInstanceId(parentId);
        instance.setRootInstanceId(rootId);
        instance.setStatus(InstanceStatus.RUNNING);
        return instance;
    }

    private static final class RecordingMapper implements InstanceMapper {
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

        private final Map<Long, Instance> instances = new LinkedHashMap<>();

        @Override
        public Instance selectCurrentById(Long id) {
            return selectById(id);
        }

        @Override
        public Instance selectById(Long id) {
            return instances.get(id);
        }

        @Override
        public List<Instance> selectByParentInstanceId(Long id) {
            return List.of();
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
            return 0;
        }

        @Override
        public int updateById(
                Instance instance,
                com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            return 0;
        }

        @Override
        public int markRunningIfWaiting(
                Long id,
                LocalDateTime startTime,
                com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            return 0;
        }

        @Override
        public int markWaitingPermitIfWaiting(
                Long id,
                String message,
                LocalDateTime deadline,
                com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            return 0;
        }

        @Override
        public int markPausedIfRunning(
                Long id, com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            return 0;
        }

        @Override
        public int markRunningIfPaused(
                Long id, com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            return 0;
        }

        @Override
        public int markWaitingEventIfRunning(
                Long id, com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            return 0;
        }

        @Override
        public int resetWaitingEventForContinuation(
                Long id, com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            return 0;
        }

        @Override
        public int updateProgressIfRunning(
                Long id,
                Integer progress,
                com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            return 0;
        }

        @Override
        public int updateInputVariablesIfWaiting(
                Long id,
                String inputVariables,
                com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            return 0;
        }

        @Override
        public int updateOutputVariablesIfRunning(
                Long id,
                String outputVariables,
                com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            return 0;
        }

        @Override
        public int completeIfCurrent(
                Instance instance,
                InstanceStatus expectedStatus,
                com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            return 0;
        }

        @Override
        public int resetForRetry(
                Long id,
                InstanceStatus expectedStatus,
                com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit permit) {
            return 0;
        }

        @Override
        public int adoptActiveTree(Long rootInstanceId, long fenceToken, String claimToken) {
            return 0;
        }

        @Override
        public int adoptTreeForRetry(Long rootInstanceId, long fenceToken, String claimToken) {
            return 0;
        }
    }
}
