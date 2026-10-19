// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.task.definition;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.MessageDefinition;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.task.TaskUpsertRequest;
import com.ifielsc.ijob.core.error.CoreMessage;
import com.ifielsc.ijob.infra.entity.task.TaskDefinition;
import com.ifielsc.ijob.infra.mapper.task.TaskCurrentDefinitionAdvance;
import com.ifielsc.ijob.infra.mapper.task.TaskCurrentDefinitionInitialization;
import com.ifielsc.ijob.infra.mapper.task.TaskDefinitionGroupMapper;
import com.ifielsc.ijob.infra.mapper.task.TaskDefinitionMapper;
import com.ifielsc.ijob.infra.mapper.task.TaskPublicationIdentityInsert;
import com.ifielsc.ijob.infra.mapper.task.TaskPublicationIdentityRow;
import com.ifielsc.ijob.infra.mapper.task.TaskPublicationMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class TaskDefinitionPublicationService {
    private static final long INITIAL_REVISION = 1L;

    private final TaskDefinitionValidator definitionValidator;
    private final TaskPluginAvailabilityService availabilityService;
    private final TaskPluginConfigPublicationValidationService configValidationService;
    private final TaskPluginConfigSnapshotService configSnapshotService;
    private final TaskPublicationMapper publicationMapper;
    private final TaskDefinitionMapper definitionMapper;
    private final TaskDefinitionGroupMapper definitionGroupMapper;

    public TaskDefinitionPublicationService(
            TaskDefinitionValidator definitionValidator,
            TaskPluginAvailabilityService availabilityService,
            TaskPluginConfigPublicationValidationService configValidationService,
            TaskPluginConfigSnapshotService configSnapshotService,
            TaskPublicationMapper publicationMapper,
            TaskDefinitionMapper definitionMapper,
            TaskDefinitionGroupMapper definitionGroupMapper) {
        this.definitionValidator = definitionValidator;
        this.availabilityService = availabilityService;
        this.configValidationService = configValidationService;
        this.configSnapshotService = configSnapshotService;
        this.publicationMapper = publicationMapper;
        this.definitionMapper = definitionMapper;
        this.definitionGroupMapper = definitionGroupMapper;
    }

    @Transactional
    public TaskPublicationResult create(TaskUpsertRequest request) {
        if (request != null) ApiCallHeaders.merge(request, null, null, null);
        definitionValidator.validate(request);
        validatePluginConfig(request);
        snapshotPluginConfig(request);

        LocalDateTime now = LocalDateTime.now();
        TaskPublicationIdentityInsert identity = toIdentity(request, now);
        requireInserted(
                publicationMapper.insertIdentity(identity),
                identity.getId(),
                CoreMessage.TASK_IDENTITY_CREATION_FAILED);

        TaskDefinition definition =
                writeDefinition(identity.getId(), INITIAL_REVISION, request, now);
        TaskCurrentDefinitionInitialization initialization =
                new TaskCurrentDefinitionInitialization();
        initialization.setTaskId(identity.getId());
        initialization.setDefinitionId(definition.getId());
        initialization.setUpdateTime(now);
        requireAffected(
                publicationMapper.initializeCurrentDefinition(initialization),
                CoreMessage.INITIAL_TASK_DEFINITION_ACTIVATION_FAILED);
        return new TaskPublicationResult(
                identity.getId(), definition.getId(), definition.getRevision());
    }

    @Transactional
    public TaskPublicationResult publish(
            Long taskId, Long expectedRevision, TaskUpsertRequest request) {
        requirePositiveTaskId(taskId);
        requirePositiveExpectedRevision(expectedRevision);
        if (request != null
                && (request.getPreservedHeaderNames() != null
                        || request.getPreservedRemoteStopHeaderNames() != null)) {
            TaskDefinition source =
                    request.getHeaderSourceDefinitionId() == null
                            ? null
                            : definitionMapper.selectById(request.getHeaderSourceDefinitionId());
            ApiCallHeaders.merge(request, source, taskId, expectedRevision);
        }
        definitionValidator.validate(request);
        validatePluginConfig(request);
        snapshotPluginConfig(request);

        TaskPublicationIdentityRow identity = publicationMapper.selectIdentityForUpdate(taskId);
        validateCurrentIdentity(taskId, identity, expectedRevision);
        validateCurrentDefinition(identity);
        long nextRevision = nextRevision(expectedRevision);

        LocalDateTime now = LocalDateTime.now();
        TaskDefinition definition = writeDefinition(taskId, nextRevision, request, now);
        requireAffected(
                publicationMapper.advanceCurrentDefinition(toAdvance(identity, definition, now)),
                CoreMessage.TASK_CURRENT_DEFINITION_ACTIVATION_FAILED);
        return new TaskPublicationResult(taskId, definition.getId(), definition.getRevision());
    }

    private void validateCurrentIdentity(
            Long taskId, TaskPublicationIdentityRow identity, Long expectedRevision) {
        if (identity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, CoreMessage.TASK_DOES_NOT_EXIST);
        }
        if (!taskId.equals(identity.getId())) {
            throw systemError(CoreMessage.TASK_IDENTITY_LOCK_RESULT_IS_INCONSISTENT);
        }
        validateRetiredState(identity);
        if (identity.getRetired()) {
            throw new BusinessException(ResultCode.CONFLICT, CoreMessage.TASK_IS_RETIRED);
        }

        Long definitionId = identity.getCurrentTaskDefinitionId();
        Long revision = identity.getCurrentRevision();
        if (definitionId == null && revision == null) {
            throw new BusinessException(
                    ResultCode.CONFLICT, CoreMessage.TASK_HAS_NO_BACKFILLED_CURRENT_DEFINITION);
        }
        if (definitionId == null || revision == null || definitionId <= 0 || revision <= 0) {
            throw systemError(CoreMessage.TASK_CURRENT_POINTER_DATA_IS_INCOMPLETE);
        }
        if (!revision.equals(expectedRevision)) {
            throw new BusinessException(ResultCode.CONFLICT, CoreMessage.TASK_REVISION_HAS_CHANGED);
        }
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

    private long nextRevision(Long expectedRevision) {
        try {
            return Math.addExact(expectedRevision, 1L);
        } catch (ArithmeticException exception) {
            throw new BusinessException(
                    ResultCode.CONFLICT,
                    CoreMessage.TASK_REVISION_HAS_REACHED_ITS_LIMIT,
                    exception);
        }
    }

    private TaskDefinition writeDefinition(
            Long taskId, long revision, TaskUpsertRequest request, LocalDateTime now) {
        TaskDefinition definition = toDefinition(taskId, revision, request, now);
        requireInserted(
                definitionMapper.insert(definition),
                definition.getId(),
                CoreMessage.TASK_DEFINITION_CREATION_FAILED);
        for (String group : request.getGroups()) {
            requireAffected(
                    definitionGroupMapper.insert(definition.getId(), group, now),
                    CoreMessage.TASK_DEFINITION_CREATION_FAILED);
        }
        return definition;
    }

    private void snapshotPluginConfig(TaskUpsertRequest request) {
        request.setPluginConfig(
                configSnapshotService.snapshot(request.getPluginType(), request.getPluginConfig()));
    }

    private void validatePluginConfig(TaskUpsertRequest request) {
        availabilityService.requireEnabledForPublication(request.getPluginType());
        configValidationService.validate(
                request.getPluginType(),
                request.getPluginConfig(),
                new TaskPluginConfigPublicationContext(
                        resolveRetryTimes(request), request.getTimeout()));
    }

    private TaskPublicationIdentityInsert toIdentity(TaskUpsertRequest request, LocalDateTime now) {
        TaskPublicationIdentityInsert identity = new TaskPublicationIdentityInsert();
        identity.setName(request.getName().trim());
        identity.setPluginType(request.getPluginType());
        identity.setPluginConfig(request.getPluginConfig());
        identity.setRetryTimes(resolveRetryTimes(request));
        identity.setRetryInterval(resolveRetryInterval(request));
        identity.setTimeout(request.getTimeout());
        identity.setEnabled(resolveEnabled(request));
        identity.setDescription(request.getDescription());
        identity.setCreateTime(now);
        identity.setUpdateTime(now);
        return identity;
    }

    private TaskDefinition toDefinition(
            Long taskId, long revision, TaskUpsertRequest request, LocalDateTime now) {
        TaskDefinition definition = new TaskDefinition();
        definition.setTaskId(taskId);
        definition.setRevision(revision);
        definition.setName(request.getName().trim());
        definition.setPluginType(request.getPluginType());
        definition.setPluginConfig(request.getPluginConfig());
        definition.setRetryTimes(resolveRetryTimes(request));
        definition.setRetryInterval(resolveRetryInterval(request));
        definition.setTimeout(request.getTimeout());
        definition.setEnabled(resolveEnabled(request));
        definition.setDescription(request.getDescription());
        definition.setCreateTime(now);
        return definition;
    }

    private TaskCurrentDefinitionAdvance toAdvance(
            TaskPublicationIdentityRow identity, TaskDefinition definition, LocalDateTime now) {
        TaskCurrentDefinitionAdvance advance = new TaskCurrentDefinitionAdvance();
        advance.setTaskId(identity.getId());
        advance.setExpectedDefinitionId(identity.getCurrentTaskDefinitionId());
        advance.setExpectedRevision(identity.getCurrentRevision());
        advance.setDefinitionId(definition.getId());
        advance.setNextRevision(definition.getRevision());
        advance.setName(definition.getName());
        advance.setPluginType(definition.getPluginType());
        advance.setPluginConfig(definition.getPluginConfig());
        advance.setRetryTimes(definition.getRetryTimes());
        advance.setRetryInterval(definition.getRetryInterval());
        advance.setTimeout(definition.getTimeout());
        advance.setEnabled(definition.getEnabled());
        advance.setDescription(definition.getDescription());
        advance.setUpdateTime(now);
        return advance;
    }

    private int resolveRetryTimes(TaskUpsertRequest request) {
        return request.getRetryTimes() == null
                ? TaskDefinitionValidator.DEFAULT_RETRY_TIMES
                : request.getRetryTimes();
    }

    private int resolveRetryInterval(TaskUpsertRequest request) {
        return request.getRetryInterval() == null
                ? TaskDefinitionValidator.DEFAULT_RETRY_INTERVAL
                : request.getRetryInterval();
    }

    private boolean resolveEnabled(TaskUpsertRequest request) {
        return request.getEnabled() == null
                ? TaskDefinitionValidator.DEFAULT_ENABLED
                : request.getEnabled();
    }

    private void requirePositiveTaskId(Long taskId) {
        if (taskId == null || taskId <= 0) {
            throw new BusinessException(
                    ResultCode.PARAM_ERROR, CoreMessage.TASK_ID_MUST_BE_GREATER_THAN_ZERO);
        }
    }

    private void requirePositiveExpectedRevision(Long expectedRevision) {
        if (expectedRevision == null || expectedRevision <= 0) {
            throw new BusinessException(
                    ResultCode.PARAM_ERROR,
                    CoreMessage.EXPECTED_REVISION_MUST_BE_GREATER_THAN_ZERO);
        }
    }

    private void requireInserted(int affectedRows, Long generatedId, MessageDefinition message) {
        if (affectedRows != 1 || generatedId == null || generatedId <= 0) {
            throw systemError(message);
        }
    }

    private void requireAffected(int affectedRows, MessageDefinition message) {
        if (affectedRows != 1) {
            throw systemError(message);
        }
    }

    private BusinessException systemError(MessageDefinition message, Object... arguments) {
        return new BusinessException(ResultCode.SYSTEM_ERROR, message, arguments);
    }
}
