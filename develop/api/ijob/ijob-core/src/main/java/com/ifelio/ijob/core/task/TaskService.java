package com.ifelio.ijob.core.task;

import com.github.pagehelper.PageHelper;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.PageResult;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.common.routing.ExecutionGroupCodes;
import com.ifelio.ijob.contract.common.BasePageQuery;
import com.ifelio.ijob.contract.common.BatchDeleteRequest;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.contract.schedule.ScheduleDTO;
import com.ifelio.ijob.contract.task.TaskDetailDTO;
import com.ifelio.ijob.contract.task.TaskListDTO;
import com.ifelio.ijob.contract.task.TaskPageQuery;
import com.ifelio.ijob.contract.task.TaskPublicationDTO;
import com.ifelio.ijob.contract.task.TaskUpsertRequest;
import com.ifelio.ijob.core.error.CoreMessage;
import com.ifelio.ijob.core.management.BatchDeleteValidator;
import com.ifelio.ijob.core.notify.NotifyBindingService;
import com.ifelio.ijob.core.query.PageResults;
import com.ifelio.ijob.core.schedule.ScheduleService;
import com.ifelio.ijob.core.task.definition.TaskDefinitionPublicationService;
import com.ifelio.ijob.core.task.definition.TaskPublicationResult;
import com.ifelio.ijob.core.task.retirement.TaskRetirementResult;
import com.ifelio.ijob.core.task.retirement.TaskRetirementService;
import com.ifelio.ijob.infra.entity.task.Task;
import com.ifelio.ijob.infra.mapper.group.DefinitionGroupRow;
import com.ifelio.ijob.infra.mapper.task.TaskDefinitionGroupMapper;
import com.ifelio.ijob.infra.mapper.task.TaskMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskService {
    private final TaskMapper taskMapper;
    private final TaskDefinitionGroupMapper definitionGroupMapper;
    private final ScheduleService scheduleService;
    private final NotifyBindingService notifyBindingService;
    private final TaskDefinitionPublicationService publicationService;
    private final TaskRetirementService retirementService;

    public PageResult<TaskListDTO> queryPage(TaskPageQuery query) {
        TaskPageQuery resolvedQuery = query == null ? new TaskPageQuery() : query;
        if (resolvedQuery.getId() != null) requirePositiveId(resolvedQuery.getId());
        normalizeGroupFilter(resolvedQuery);
        PageHelper.startPage(resolvedQuery.getPageNo(), resolvedQuery.getPageSize());
        PageResult<TaskListDTO> result;
        try {
            result = PageResults.from(taskMapper.selectDtoList(resolvedQuery));
        } finally {
            PageHelper.clearPage();
        }
        attachGroups(result.getList());
        return result;
    }

    public TaskDetailDTO getDetail(Long id) {
        requirePositiveId(id);
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, CoreMessage.TASK_DOES_NOT_EXIST);
        }

        TaskDetailDTO detail = new TaskDetailDTO();
        BeanUtils.copyProperties(task, detail);
        com.ifelio.ijob.core.task.definition.ApiCallHeaders.project(detail);
        detail.setGroups(
                groupsFor(List.of(task.getCurrentTaskDefinitionId()))
                        .getOrDefault(task.getCurrentTaskDefinitionId(), List.of()));
        List<ScheduleDTO> schedules =
                scheduleService.listScheduleDetailsByTargetId(TargetType.TASK, id);
        detail.setSchedules(schedules);
        detail.setNotifyBindings(notifyBindingService.listDetailsByTarget(TargetType.TASK, id));
        return detail;
    }

    public PageResult<String> queryGroups(BasePageQuery query, String prefix) {
        BasePageQuery resolved = query == null ? new BasePageQuery() : query;
        String normalized = prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9._-]{0,64}")) {
            throw new BusinessException(
                    ResultCode.PARAM_ERROR, CoreMessage.EXECUTION_GROUPS_ARE_INVALID);
        }
        String pattern = normalized.replace("_", "!_") + "%";
        PageHelper.startPage(resolved.getPageNo(), Math.min(resolved.getPageSize(), 50));
        try {
            return PageResults.from(taskMapper.selectGroupCodes(pattern));
        } finally {
            PageHelper.clearPage();
        }
    }

    private void normalizeGroupFilter(TaskPageQuery query) {
        if (query.getGroup() == null) {
            return;
        }
        try {
            query.setGroup(ExecutionGroupCodes.normalize(List.of(query.getGroup())).get(0));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ResultCode.PARAM_ERROR,
                    CoreMessage.EXECUTION_GROUPS_ARE_INVALID,
                    exception.getMessage());
        }
    }

    private void attachGroups(List<TaskListDTO> tasks) {
        Map<Long, List<String>> groups =
                groupsFor(tasks.stream().map(TaskListDTO::getCurrentTaskDefinitionId).toList());
        tasks.forEach(
                task ->
                        task.setGroups(
                                groups.getOrDefault(task.getCurrentTaskDefinitionId(), List.of())));
    }

    private Map<Long, List<String>> groupsFor(List<Long> definitionIds) {
        if (definitionIds.isEmpty()) {
            return Map.of();
        }
        List<DefinitionGroupRow> rows = definitionGroupMapper.selectByDefinitionIds(definitionIds);
        if (rows == null) {
            throw new IllegalStateException("Task definition group query returned null");
        }
        return rows.stream()
                .collect(
                        Collectors.groupingBy(
                                DefinitionGroupRow::definitionId,
                                Collectors.mapping(
                                        DefinitionGroupRow::groupCode,
                                        Collectors.toUnmodifiableList())));
    }

    public TaskPublicationDTO create(TaskUpsertRequest request) {
        TaskPublicationResult result = publicationService.create(request);
        log.info(
                "initial task revision published, taskId={}, revision={}",
                result.taskId(),
                result.revision());
        return toDto(result);
    }

    public TaskPublicationDTO update(
            Long taskId, Long expectedRevision, TaskUpsertRequest request) {
        TaskPublicationResult result =
                publicationService.publish(taskId, expectedRevision, request);
        log.info(
                "task revision published, taskId={}, revision={}",
                result.taskId(),
                result.revision());
        return toDto(result);
    }

    public void delete(Long taskId) {
        TaskRetirementResult result = retirementService.retire(taskId);
        log.info(
                "task retired, taskId={}, alreadyRetired={}",
                result.taskId(),
                result.alreadyRetired());
    }

    @Transactional
    public void deleteBatch(BatchDeleteRequest request) {
        List<Long> ids = BatchDeleteValidator.validate(request);
        retirementService.retireBatch(ids);
        log.info("tasks retired, count={}", ids.size());
    }

    private TaskPublicationDTO toDto(TaskPublicationResult result) {
        return new TaskPublicationDTO(result.taskId(), result.definitionId(), result.revision());
    }

    private void requirePositiveId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(
                    ResultCode.PARAM_ERROR, CoreMessage.TASK_ID_MUST_BE_GREATER_THAN_ZERO);
        }
    }
}
