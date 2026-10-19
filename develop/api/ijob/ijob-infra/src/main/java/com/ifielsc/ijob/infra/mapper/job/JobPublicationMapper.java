// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.job;

import com.ifielsc.ijob.infra.entity.job.Job;

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
