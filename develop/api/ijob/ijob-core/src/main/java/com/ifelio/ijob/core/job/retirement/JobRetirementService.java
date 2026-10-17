package com.ifelio.ijob.core.job.retirement;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.MessageDefinition;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.core.error.CoreMessage;
import com.ifelio.ijob.infra.entity.job.JobDefinition;
import com.ifelio.ijob.infra.mapper.job.JobDefinitionMapper;
import com.ifelio.ijob.infra.mapper.job.JobIdentityRetirement;
import com.ifelio.ijob.infra.mapper.job.JobPublicationIdentityRow;
import com.ifelio.ijob.infra.mapper.job.JobPublicationMapper;
import com.ifelio.ijob.infra.mapper.schedule.ScheduleMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JobRetirementService {
    private final JobPublicationMapper publicationMapper;
    private final JobDefinitionMapper definitionMapper;
    private final ScheduleMapper scheduleMapper;
    private final com.ifelio.ijob.infra.mapper.event.EventBindingMapper eventBindingMapper;

    @Transactional
    public JobRetirementResult retire(Long jobId) {
        requirePositiveJobId(jobId);
        lockPublicationBarrier();
        return retireLocked(jobId);
    }

    @Transactional
    public List<JobRetirementResult> retireBatch(List<Long> jobIds) {
        validateOrderedIds(jobIds);
        lockPublicationBarrier();
        List<JobRetirementResult> results = new ArrayList<>(jobIds.size());
        for (Long jobId : jobIds) {
            results.add(retireLocked(jobId));
        }
        return List.copyOf(results);
    }

    private JobRetirementResult retireLocked(Long jobId) {
        JobPublicationIdentityRow identity = publicationMapper.selectIdentityForUpdate(jobId);
        validateIdentity(jobId, identity);
        if (identity.getRetired()) {
            disableSchedules(jobId);
            return new JobRetirementResult(jobId, identity.getRetiredTime(), true);
        }

        if (identity.getCurrentDefinitionId() != null) {
            validateCurrentDefinition(identity);
        }
        LocalDateTime now = LocalDateTime.now();
        JobIdentityRetirement retirement = new JobIdentityRetirement();
        retirement.setJobId(jobId);
        retirement.setExpectedDefinitionId(identity.getCurrentDefinitionId());
        retirement.setExpectedRevision(identity.getCurrentRevision());
        retirement.setRetiredTime(now);
        if (publicationMapper.retireIdentity(retirement) != 1) {
            throw systemError(CoreMessage.JOB_RETIREMENT_TRANSITION_FAILED);
        }
        disableSchedules(jobId);
        return new JobRetirementResult(jobId, now, false);
    }

    private void validateOrderedIds(List<Long> jobIds) {
        if (jobIds == null || jobIds.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, CoreMessage.JOB_ID_IS_REQUIRED);
        }
        Long previous = null;
        for (Long jobId : jobIds) {
            requirePositiveJobId(jobId);
            if (previous != null && jobId <= previous) {
                throw new BusinessException(
                        ResultCode.PARAM_ERROR,
                        CoreMessage.JOB_ID_MUST_BE_DISTINCT_AND_LOCKED_IN_ASCENDING_ORDER);
            }
            previous = jobId;
        }
    }

    private void disableSchedules(Long jobId) {
        scheduleMapper.disableByTargetId(TargetType.JOB, jobId);
        eventBindingMapper.disableByTargetId(TargetType.JOB, jobId, false, LocalDateTime.now());
    }

    private void lockPublicationBarrier() {
        List<Long> lockedIds = publicationMapper.lockAllJobIdsForPublication();
        if (lockedIds == null) {
            throw systemError(CoreMessage.JOB_PUBLICATION_BARRIER_RETURNED_NULL);
        }
        Long previous = null;
        for (Long lockedId : lockedIds) {
            if (lockedId == null || lockedId <= 0 || previous != null && lockedId <= previous) {
                throw systemError(CoreMessage.JOB_PUBLICATION_BARRIER_DATA_IS_INCOMPLETE);
            }
            previous = lockedId;
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
        Long definitionId = identity.getCurrentDefinitionId();
        Long revision = identity.getCurrentRevision();
        if (definitionId == null && revision == null) {
            return;
        }
        if (definitionId == null || revision == null || definitionId <= 0 || revision <= 0) {
            throw systemError(CoreMessage.JOB_CURRENT_POINTER_DATA_IS_INCOMPLETE);
        }
    }

    private void validateCurrentDefinition(JobPublicationIdentityRow identity) {
        JobDefinition definition = definitionMapper.selectById(identity.getCurrentDefinitionId());
        if (definition == null
                || !identity.getCurrentDefinitionId().equals(definition.getId())
                || !identity.getId().equals(definition.getJobId())
                || !identity.getCurrentRevision().equals(definition.getRevision())) {
            throw systemError(CoreMessage.JOB_CURRENT_DEFINITION_OWNERSHIP_IS_INCONSISTENT);
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
