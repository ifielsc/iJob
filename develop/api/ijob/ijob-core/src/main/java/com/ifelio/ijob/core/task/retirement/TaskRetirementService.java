package com.ifelio.ijob.core.task.retirement;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.MessageDefinition;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.core.error.CoreMessage;
import com.ifelio.ijob.infra.entity.task.TaskDefinition;
import com.ifelio.ijob.infra.mapper.schedule.ScheduleMapper;
import com.ifelio.ijob.infra.mapper.task.TaskDefinitionMapper;
import com.ifelio.ijob.infra.mapper.task.TaskIdentityRetirement;
import com.ifelio.ijob.infra.mapper.task.TaskPublicationIdentityRow;
import com.ifelio.ijob.infra.mapper.task.TaskPublicationMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskRetirementService {
    private final TaskPublicationMapper publicationMapper;
    private final TaskDefinitionMapper definitionMapper;
    private final ScheduleMapper scheduleMapper;
    private final com.ifelio.ijob.infra.mapper.event.EventBindingMapper eventBindingMapper;

    @Transactional
    public TaskRetirementResult retire(Long taskId) {
        requirePositiveTaskId(taskId);
        return retireLocked(taskId);
    }

    @Transactional
    public List<TaskRetirementResult> retireBatch(List<Long> taskIds) {
        validateOrderedIds(taskIds);
        List<TaskRetirementResult> results = new ArrayList<>(taskIds.size());
        for (Long taskId : taskIds) {
            results.add(retireLocked(taskId));
        }
        return List.copyOf(results);
    }

    private TaskRetirementResult retireLocked(Long taskId) {
        LocalDateTime now = LocalDateTime.now();

        TaskPublicationIdentityRow identity = publicationMapper.selectIdentityForUpdate(taskId);
        validateIdentity(taskId, identity);
        if (identity.getRetired()) {
            disableSchedules(taskId);
            return new TaskRetirementResult(taskId, identity.getRetiredTime(), true);
        }

        if (identity.getCurrentTaskDefinitionId() != null) {
            validateCurrentDefinition(identity);
        }
        TaskIdentityRetirement retirement = new TaskIdentityRetirement();
        retirement.setTaskId(taskId);
        retirement.setExpectedDefinitionId(identity.getCurrentTaskDefinitionId());
        retirement.setExpectedRevision(identity.getCurrentRevision());
        retirement.setRetiredTime(now);
        if (publicationMapper.retireIdentity(retirement) != 1) {
            throw systemError(CoreMessage.TASK_RETIREMENT_TRANSITION_FAILED);
        }
        disableSchedules(taskId);
        return new TaskRetirementResult(taskId, now, false);
    }

    private void validateOrderedIds(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, CoreMessage.TASK_ID_IS_REQUIRED);
        }
        Long previous = null;
        for (Long taskId : taskIds) {
            requirePositiveTaskId(taskId);
            if (previous != null && taskId <= previous) {
                throw new BusinessException(
                        ResultCode.PARAM_ERROR,
                        CoreMessage.TASK_ID_MUST_BE_DISTINCT_AND_LOCKED_IN_ASCENDING_ORDER);
            }
            previous = taskId;
        }
    }

    private void disableSchedules(Long taskId) {
        scheduleMapper.disableByTargetId(TargetType.TASK, taskId);
        eventBindingMapper.disableByTargetId(TargetType.TASK, taskId, false, LocalDateTime.now());
    }

    private void validateIdentity(Long taskId, TaskPublicationIdentityRow identity) {
        if (identity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, CoreMessage.TASK_DOES_NOT_EXIST);
        }
        if (!taskId.equals(identity.getId())) {
            throw systemError(CoreMessage.TASK_IDENTITY_LOCK_RESULT_IS_INCONSISTENT);
        }
        validateRetiredState(identity);
        validatePointerState(identity);
    }

    private void validateRetiredState(TaskPublicationIdentityRow identity) {
        Boolean retired = identity.getRetired();
        LocalDateTime retiredTime = identity.getRetiredTime();
        if (retired == null
                || (retired && retiredTime == null)
                || (!retired && retiredTime != null)) {
            throw systemError(CoreMessage.TASK_RETIRED_STATE_DATA_IS_INCOMPLETE);
        }
    }

    private void validatePointerState(TaskPublicationIdentityRow identity) {
        Long definitionId = identity.getCurrentTaskDefinitionId();
        Long revision = identity.getCurrentRevision();
        if (definitionId == null && revision == null) {
            return;
        }
        if (definitionId == null || revision == null || definitionId <= 0 || revision <= 0) {
            throw systemError(CoreMessage.TASK_CURRENT_POINTER_DATA_IS_INCOMPLETE);
        }
    }

    private void validateCurrentDefinition(TaskPublicationIdentityRow identity) {
        TaskDefinition definition =
                definitionMapper.selectById(identity.getCurrentTaskDefinitionId());
        if (definition == null
                || !identity.getCurrentTaskDefinitionId().equals(definition.getId())
                || !identity.getId().equals(definition.getTaskId())
                || !identity.getCurrentRevision().equals(definition.getRevision())) {
            throw systemError(CoreMessage.TASK_CURRENT_DEFINITION_OWNERSHIP_IS_INCONSISTENT);
        }
    }

    private void requirePositiveTaskId(Long taskId) {
        if (taskId == null || taskId <= 0) {
            throw new BusinessException(
                    ResultCode.PARAM_ERROR, CoreMessage.TASK_ID_MUST_BE_GREATER_THAN_ZERO);
        }
    }

    private BusinessException systemError(MessageDefinition message, Object... arguments) {
        return new BusinessException(ResultCode.SYSTEM_ERROR, message, arguments);
    }
}
