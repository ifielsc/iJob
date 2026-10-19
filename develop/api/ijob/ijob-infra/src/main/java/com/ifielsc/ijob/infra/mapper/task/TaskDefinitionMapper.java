package com.ifielsc.ijob.infra.mapper.task;

import com.ifielsc.ijob.infra.entity.task.TaskDefinition;

import java.util.List;

public interface TaskDefinitionMapper {
    TaskDefinition selectById(Long id);

    List<TaskDefinition> selectListByTaskId(Long taskId);

    int insert(TaskDefinition definition);
}
