package com.ifelio.ijob.infra.mapper.job;

import com.ifelio.ijob.contract.job.JobListDTO;
import com.ifelio.ijob.contract.job.JobPageQuery;

import java.util.List;

public interface JobCurrentDefinitionMapper {
    List<JobListDTO> selectDtoList(JobPageQuery query);

    JobCurrentDefinitionRow selectById(Long id);
}
