package com.ifielsc.ijob.infra.mapper.job;

import com.ifielsc.ijob.contract.job.JobListDTO;
import com.ifielsc.ijob.contract.job.JobPageQuery;

import java.util.List;

public interface JobCurrentDefinitionMapper {
    List<JobListDTO> selectDtoList(JobPageQuery query);

    JobCurrentDefinitionRow selectById(Long id);
}
