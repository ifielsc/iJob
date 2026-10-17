package com.ifelio.ijob.infra.mapper.job;

import com.ifelio.ijob.infra.entity.job.JobDefinitionEdge;

import java.util.List;

public interface JobDefinitionEdgeMapper {
    List<JobDefinitionEdge> selectListByDefinitionId(Long definitionId);

    int insert(JobDefinitionEdge edge);
}
