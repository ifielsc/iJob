package com.ifelio.ijob.infra.mapper.task;

import com.ifelio.ijob.infra.entity.task.TaskDefinition;

import java.util.List;

public interface TaskDefinitionMapper {
    TaskDefinition selectById(Long id);

    List<TaskDefinition> selectListByTaskId(Long taskId);

    int insert(TaskDefinition definition);
}
