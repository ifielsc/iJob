package com.ifelio.ijob.infra.mapper.task;

import com.ifelio.ijob.contract.task.TaskVersionDTO;

import java.util.List;

public interface TaskHistoryMapper {
    List<TaskVersionDTO> selectVersions(Long taskId);
}
