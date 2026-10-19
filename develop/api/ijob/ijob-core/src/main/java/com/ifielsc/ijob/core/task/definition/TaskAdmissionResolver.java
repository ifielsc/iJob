// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.task.definition;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.MessageDefinition;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.core.error.CoreMessage;
import com.ifielsc.ijob.infra.entity.task.TaskDefinition;
import com.ifielsc.ijob.infra.mapper.task.TaskDefinitionMapper;
import com.ifielsc.ijob.infra.mapper.task.TaskPublicationIdentityRow;
import com.ifielsc.ijob.infra.mapper.task.TaskPublicationMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskAdmissionResolver {
    private final TaskPublicationMapper publicationMapper;
    private final TaskDefinitionMapper definitionMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public TaskAdmissionSnapshot resolve(Long taskId) {
        requirePositiveTaskId(taskId);
        TaskPublicationIdentityRow identity = publicationMapper.selectIdentityForUpdate(taskId);
        validateIdentity(taskId, identity);

        TaskDefinition definition =
                definitionMapper.selectById(identity.getCurrentTaskDefinitionId());
        validateDefinition(identity, definition);
        if (!definition.getEnabled()) {
            throw new BusinessException(ResultCode.CONFLICT, CoreMessage.TASK_IS_DISABLED);
        }

        return new TaskAdmissionSnapshot(
                taskId, definition.getId(), definition.getRevision(), toExecutionSpec(definition));
    }

    /**
     * Expected unavailable subscribers do not escape the transaction interceptor or poison fan-out.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public TaskAdmissionSnapshot resolveForEvent(Long taskId) {
        try {
            return resolve(taskId);
        } catch (BusinessException exception) {
            if (java.util.Set.of(
                            CoreMessage.TASK_IS_RETIRED.errorCode(),
                            CoreMessage.TASK_IS_DISABLED.errorCode(),
                            CoreMessage.TASK_HAS_NO_PUBLISHED_DEFINITION.errorCode(),
                            CoreMessage.TASK_DOES_NOT_EXIST.errorCode())
                    .contains(exception.getErrorCode())) {
                return null;
            }
            throw exception;
        }
    }

    private void validateIdentity(Long taskId, TaskPublicationIdentityRow identity) {
        if (identity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, CoreMessage.TASK_DOES_NOT_EXIST);
        }
        if (!taskId.equals(identity.getId())) {
            throw systemError(CoreMessage.TASK_IDENTITY_LOCK_RESULT_IS_INCONSISTENT);
        }

        Boolean retired = identity.getRetired();
        if (retired == null
                || (retired && identity.getRetiredTime() == null)
                || (!retired && identity.getRetiredTime() != null)) {
            throw systemError(CoreMessage.TASK_RETIRED_STATE_DATA_IS_INCOMPLETE);
        }
        if (retired) {
            throw new BusinessException(ResultCode.CONFLICT, CoreMessage.TASK_IS_RETIRED);
        }

        Long definitionId = identity.getCurrentTaskDefinitionId();
        Long revision = identity.getCurrentRevision();
        if (definitionId == null && revision == null) {
            throw new BusinessException(
                    ResultCode.CONFLICT, CoreMessage.TASK_HAS_NO_PUBLISHED_DEFINITION);
        }
        if (definitionId == null || revision == null || definitionId <= 0 || revision <= 0) {
            throw systemError(CoreMessage.TASK_CURRENT_POINTER_DATA_IS_INCOMPLETE);
        }
    }

    private void validateDefinition(
            TaskPublicationIdentityRow identity, TaskDefinition definition) {
        if (definition == null
                || !identity.getCurrentTaskDefinitionId().equals(definition.getId())
                || !identity.getId().equals(definition.getTaskId())
                || !identity.getCurrentRevision().equals(definition.getRevision())) {
            throw systemError(CoreMessage.TASK_CURRENT_DEFINITION_OWNERSHIP_IS_INCONSISTENT);
        }
        if (definition.getName() == null
                || definition.getName().isBlank()
                || definition.getPluginType() == null
                || definition.getPluginConfig() == null
                || definition.getPluginConfig().isBlank()
                || definition.getRetryTimes() == null
                || definition.getRetryTimes() < 0
                || definition.getRetryInterval() == null
                || definition.getRetryInterval() < 0
                || definition.getTimeout() != null && definition.getTimeout() <= 0
                || definition.getEnabled() == null
                || definition.getCreateTime() == null) {
            throw systemError(CoreMessage.TASK_DEFINITION_EXECUTION_FIELDS_IS_INCOMPLETE);
        }
    }

    private TaskExecutionSpec toExecutionSpec(TaskDefinition definition) {
        return new TaskExecutionSpec(
                definition.getName(),
                definition.getPluginType(),
                definition.getPluginConfig(),
                definition.getRetryTimes(),
                definition.getRetryInterval(),
                definition.getTimeout(),
                definition.getEnabled(),
                definition.getDescription());
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
