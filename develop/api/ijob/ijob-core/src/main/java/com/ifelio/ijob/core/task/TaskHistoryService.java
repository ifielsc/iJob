package com.ifelio.ijob.core.task;

import com.github.pagehelper.PageHelper;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.PageResult;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.common.BasePageQuery;
import com.ifelio.ijob.contract.task.TaskDetailDTO;
import com.ifelio.ijob.contract.task.TaskVersionDTO;
import com.ifelio.ijob.core.query.PageResults;
import com.ifelio.ijob.core.task.definition.ApiCallHeaders;
import com.ifelio.ijob.infra.entity.task.Task;
import com.ifelio.ijob.infra.mapper.task.TaskDefinitionGroupMapper;
import com.ifelio.ijob.infra.mapper.task.TaskDefinitionMapper;
import com.ifelio.ijob.infra.mapper.task.TaskHistoryMapper;
import com.ifelio.ijob.infra.mapper.task.TaskMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskHistoryService {
    private final TaskMapper tasks;
    private final TaskHistoryMapper history;
    private final TaskDefinitionMapper definitions;
    private final TaskDefinitionGroupMapper groups;

    @Transactional(readOnly = true)
    public PageResult<TaskVersionDTO> versions(Long id, BasePageQuery query) {
        Task task = active(id);
        PageHelper.startPage(query.getPageNo(), query.getPageSize());
        try {
            var result = PageResults.from(history.selectVersions(id));
            result.getList()
                    .forEach(
                            v ->
                                    v.setCurrent(
                                            v.getDefinitionId()
                                                    .equals(task.getCurrentTaskDefinitionId())));
            return result;
        } finally {
            PageHelper.clearPage();
        }
    }

    @Transactional(readOnly = true)
    public TaskDetailDTO detail(Long id, Long definitionId) {
        active(id);
        if (definitionId == null || definitionId <= 0)
            throw new BusinessException(ResultCode.PARAM_ERROR);
        var definition = definitions.selectById(definitionId);
        if (definition == null || !id.equals(definition.getTaskId()))
            throw new BusinessException(ResultCode.NOT_FOUND);
        var dto = new TaskDetailDTO();
        BeanUtils.copyProperties(definition, dto);
        dto.setId(id);
        dto.setCurrentTaskDefinitionId(definitionId);
        dto.setCurrentRevision(definition.getRevision());
        dto.setUpdateTime(definition.getCreateTime());
        dto.setGroups(
                groups.selectByDefinitionIds(List.of(definitionId)).stream()
                        .map(v -> v.groupCode())
                        .toList());
        ApiCallHeaders.project(dto);
        return dto;
    }

    private Task active(Long id) {
        if (id == null || id <= 0) throw new BusinessException(ResultCode.PARAM_ERROR);
        Task task = tasks.selectById(id);
        if (task == null) throw new BusinessException(ResultCode.NOT_FOUND);
        return task;
    }
}
