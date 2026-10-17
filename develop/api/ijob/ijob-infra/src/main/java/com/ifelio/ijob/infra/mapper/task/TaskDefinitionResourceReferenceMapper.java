package com.ifelio.ijob.infra.mapper.task;

import com.ifelio.ijob.infra.entity.task.TaskDefinition;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TaskDefinitionResourceReferenceMapper {
    Long selectMaxDefinitionId();

    List<TaskDefinition> selectDefinitionBatch(
            @Param("afterId") long afterId, @Param("upperId") long upperId);
}
