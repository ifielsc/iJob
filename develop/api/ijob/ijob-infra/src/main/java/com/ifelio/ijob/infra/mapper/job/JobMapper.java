package com.ifelio.ijob.infra.mapper.job;

import com.ifelio.ijob.infra.entity.job.Job;

public interface JobMapper {
    Job selectById(Long id);
}
