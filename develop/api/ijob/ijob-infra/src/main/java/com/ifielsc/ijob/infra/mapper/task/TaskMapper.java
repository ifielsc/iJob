// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.task;

import com.ifielsc.ijob.contract.task.TaskListDTO;
import com.ifielsc.ijob.contract.task.TaskPageQuery;
import com.ifielsc.ijob.infra.entity.task.Task;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TaskMapper {
    List<TaskListDTO> selectDtoList(TaskPageQuery query);

    Task selectById(Long id);

    List<String> selectGroupCodes(@Param("pattern") String pattern);
}
