package com.ifelio.ijob.infra.mapper.job;

import com.ifelio.ijob.infra.entity.job.JobDefinition;

import java.util.List;

public interface JobDefinitionMapper {
    JobDefinition selectById(Long id);

    List<JobDefinition> selectListByJobId(Long jobId);

    int insert(JobDefinition definition);
}
