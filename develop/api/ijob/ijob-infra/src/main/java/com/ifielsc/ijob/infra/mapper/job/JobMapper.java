// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.job;

import com.ifielsc.ijob.infra.entity.job.Job;

public interface JobMapper {
    Job selectById(Long id);
}
