package com.ifelio.ijob.infra.mapper.job;

import com.ifelio.ijob.infra.entity.job.JobDefinitionNodePolicy;

import java.util.List;

public interface JobDefinitionNodePolicyMapper {
    List<JobDefinitionNodePolicy> selectListByDefinitionId(Long definitionId);

    int insert(JobDefinitionNodePolicy policy);
}
