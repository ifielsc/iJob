package com.ifelio.ijob.infra.mapper.task;

import com.ifelio.ijob.contract.task.TaskListDTO;
import com.ifelio.ijob.contract.task.TaskPageQuery;
import com.ifelio.ijob.infra.entity.task.Task;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TaskMapper {
    List<TaskListDTO> selectDtoList(TaskPageQuery query);

    Task selectById(Long id);

    List<String> selectGroupCodes(@Param("pattern") String pattern);
}
