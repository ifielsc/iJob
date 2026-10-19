package com.ifielsc.ijob.infra.mapper.job;

import com.ifielsc.ijob.infra.entity.job.Job;

public interface JobMapper {
    Job selectById(Long id);
}
