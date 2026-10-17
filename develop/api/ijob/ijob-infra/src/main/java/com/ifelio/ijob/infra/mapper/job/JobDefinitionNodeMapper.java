package com.ifelio.ijob.infra.mapper.job;

import com.ifelio.ijob.infra.entity.job.JobDefinitionNode;

import java.util.List;

public interface JobDefinitionNodeMapper {
    List<JobDefinitionNode> selectListByDefinitionId(Long definitionId);

    int insert(JobDefinitionNode node);
}
