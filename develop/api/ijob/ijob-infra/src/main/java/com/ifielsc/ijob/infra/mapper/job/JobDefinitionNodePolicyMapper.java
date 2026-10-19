// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.job;

import com.ifielsc.ijob.infra.entity.job.JobDefinitionNodePolicy;

import java.util.List;

public interface JobDefinitionNodePolicyMapper {
    List<JobDefinitionNodePolicy> selectListByDefinitionId(Long definitionId);

    int insert(JobDefinitionNodePolicy policy);
}
