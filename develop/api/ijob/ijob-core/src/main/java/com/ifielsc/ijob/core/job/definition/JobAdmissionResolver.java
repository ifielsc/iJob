// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.job.definition;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.MessageDefinition;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.core.error.CoreMessage;
import com.ifielsc.ijob.infra.entity.job.JobDefinition;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionMapper;
import com.ifielsc.ijob.infra.mapper.job.JobPublicationIdentityRow;
import com.ifielsc.ijob.infra.mapper.job.JobPublicationMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class JobAdmissionResolver {
    private final JobPublicationMapper publicationMapper;
    private final JobDefinitionMapper definitionMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public JobAdmissionSnapshot resolve(Long jobId) {
        return resolve(jobId, true);
    }

    /** Configuration import may attach a disabled schedule without admitting execution. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockPublishedConfiguration(Long jobId) {
        resolve(jobId, false);
    }

    private JobAdmissionSnapshot resolve(Long jobId, boolean requireEnabled) {
        requirePositiveJobId(jobId);
        JobPublicationIdentityRow identity = publicationMapper.selectIdentityForUpdate(jobId);
        validateIdentity(jobId, identity);

        JobDefinition definition = definitionMapper.selectById(identity.getCurrentDefinitionId());
        validateDefinition(identity, definition);
        if (requireEnabled && !definition.getEnabled()) {
            throw new BusinessException(ResultCode.CONFLICT, CoreMessage.JOB_IS_DISABLED);
        }

        return new JobAdmissionSnapshot(
                jobId,
                definition.getId(),
                definition.getRevision(),
                definition.getName(),
                definition.getTimeout());
    }

    /**
     * Expected unavailable subscribers do not escape the transaction interceptor or poison fan-out.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public JobAdmissionSnapshot resolveForEvent(Long jobId) {
        try {
            return resolve(jobId);
        } catch (BusinessException exception) {
            if (java.util.Set.of(
                            CoreMessage.JOB_IS_RETIRED.errorCode(),
                            CoreMessage.JOB_IS_DISABLED.errorCode(),
                            CoreMessage.JOB_HAS_NO_PUBLISHED_DEFINITION.errorCode(),
                            CoreMessage.JOB_DOES_NOT_EXIST.errorCode())
                    .contains(exception.getErrorCode())) {
                return null;
            }
            throw exception;
        }
    }

    private void validateIdentity(Long jobId, JobPublicationIdentityRow identity) {
        if (identity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, CoreMessage.JOB_DOES_NOT_EXIST);
        }
        if (!jobId.equals(identity.getId())) {
            throw systemError(CoreMessage.JOB_IDENTITY_LOCK_RESULT_IS_INCONSISTENT);
        }

        Boolean retired = identity.getRetired();
        LocalDateTime retiredTime = identity.getRetiredTime();
        if (retired == null
                || (retired && retiredTime == null)
                || (!retired && retiredTime != null)) {
            throw systemError(CoreMessage.JOB_RETIRED_STATE_DATA_IS_INCOMPLETE);
        }
        if (retired) {
            throw new BusinessException(ResultCode.CONFLICT, CoreMessage.JOB_IS_RETIRED);
        }

        Long definitionId = identity.getCurrentDefinitionId();
        Long revision = identity.getCurrentRevision();
        if (definitionId == null && revision == null) {
            throw new BusinessException(
                    ResultCode.CONFLICT, CoreMessage.JOB_HAS_NO_PUBLISHED_DEFINITION);
        }
        if (definitionId == null || revision == null || definitionId <= 0 || revision <= 0) {
            throw systemError(CoreMessage.JOB_CURRENT_POINTER_DATA_IS_INCOMPLETE);
        }
    }

    private void validateDefinition(JobPublicationIdentityRow identity, JobDefinition definition) {
        if (definition == null
                || !identity.getCurrentDefinitionId().equals(definition.getId())
                || !identity.getId().equals(definition.getJobId())
                || !identity.getCurrentRevision().equals(definition.getRevision())) {
            throw systemError(CoreMessage.JOB_CURRENT_DEFINITION_OWNERSHIP_IS_INCONSISTENT);
        }
        if (definition.getName() == null
                || definition.getName().isBlank()
                || definition.getTimeout() != null && definition.getTimeout() <= 0
                || definition.getEnabled() == null
                || definition.getCreateTime() == null) {
            throw systemError(CoreMessage.JOB_DEFINITION_EXECUTION_FIELDS_IS_INCOMPLETE);
        }
    }

    private void requirePositiveJobId(Long jobId) {
        if (jobId == null || jobId <= 0) {
            throw new BusinessException(
                    ResultCode.PARAM_ERROR, CoreMessage.JOB_ID_MUST_BE_GREATER_THAN_ZERO);
        }
    }

    private BusinessException systemError(MessageDefinition message, Object... arguments) {
        return new BusinessException(ResultCode.SYSTEM_ERROR, message, arguments);
    }
}
