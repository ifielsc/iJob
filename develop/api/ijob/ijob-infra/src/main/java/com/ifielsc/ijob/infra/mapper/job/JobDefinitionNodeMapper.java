package com.ifielsc.ijob.infra.mapper.job;

import com.ifielsc.ijob.infra.entity.job.JobDefinitionNode;

import java.util.List;

public interface JobDefinitionNodeMapper {
    List<JobDefinitionNode> selectListByDefinitionId(Long definitionId);

    int insert(JobDefinitionNode node);
}
