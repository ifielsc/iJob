package com.ifelio.ijob.infra.mapper.job;

import com.ifelio.ijob.infra.entity.job.Job;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface JobPublicationMapper {
    List<Long> lockAllJobIdsForPublication();

    JobPublicationIdentityRow selectIdentityForUpdate(@Param("jobId") Long jobId);

    int insertIdentity(Job identity);

    int initializeCurrentDefinition(
            @Param("jobId") Long jobId,
            @Param("definitionId") Long definitionId,
            @Param("updateTime") LocalDateTime updateTime);

    int advanceCurrentDefinition(JobCurrentDefinitionAdvance advance);

    int retireIdentity(JobIdentityRetirement retirement);
}
