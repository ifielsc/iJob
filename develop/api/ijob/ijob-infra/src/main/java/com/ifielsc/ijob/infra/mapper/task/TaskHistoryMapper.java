package com.ifielsc.ijob.infra.mapper.task;

import com.ifielsc.ijob.contract.task.TaskVersionDTO;

import java.util.List;

public interface TaskHistoryMapper {
    List<TaskVersionDTO> selectVersions(Long taskId);
}
