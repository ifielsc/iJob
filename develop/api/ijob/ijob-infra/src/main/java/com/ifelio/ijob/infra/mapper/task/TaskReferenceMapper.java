package com.ifelio.ijob.infra.mapper.task;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TaskReferenceMapper {
    List<Long> selectExistingIds(@Param("ids") List<Long> ids);
}
