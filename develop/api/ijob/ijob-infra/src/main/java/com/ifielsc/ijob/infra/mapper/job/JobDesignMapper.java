// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.job;

import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.infra.entity.job.JobDefinition;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface JobDesignMapper {
    JobDesignReference selectCurrent(@Param("type") TargetType type, @Param("id") Long id);

    JobDesignReference selectSnapshot(@Param("type") TargetType type, @Param("id") Long id);

    List<JobDefinition> selectVersions(Long jobId);

    List<String> selectGroups(@Param("pattern") String pattern);
}
