// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.job;

import com.ifielsc.ijob.infra.entity.job.JobDefinition;

import java.util.List;

public interface JobDefinitionMapper {
    JobDefinition selectById(Long id);

    List<JobDefinition> selectListByJobId(Long jobId);

    int insert(JobDefinition definition);
}
