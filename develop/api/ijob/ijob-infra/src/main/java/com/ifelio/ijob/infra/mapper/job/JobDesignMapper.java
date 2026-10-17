package com.ifelio.ijob.infra.mapper.job;

import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.infra.entity.job.JobDefinition;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface JobDesignMapper {
    JobDesignReference selectCurrent(@Param("type") TargetType type, @Param("id") Long id);

    JobDesignReference selectSnapshot(@Param("type") TargetType type, @Param("id") Long id);

    List<JobDefinition> selectVersions(Long jobId);

    List<String> selectGroups(@Param("pattern") String pattern);
}
