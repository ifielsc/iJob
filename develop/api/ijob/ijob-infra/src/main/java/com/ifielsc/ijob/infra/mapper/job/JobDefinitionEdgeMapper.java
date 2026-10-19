// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.job;

import com.ifielsc.ijob.infra.entity.job.JobDefinitionEdge;

import java.util.List;

public interface JobDefinitionEdgeMapper {
    List<JobDefinitionEdge> selectListByDefinitionId(Long definitionId);

    int insert(JobDefinitionEdge edge);
}
