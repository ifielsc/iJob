// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.common.BatchDeleteRequest;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.contract.notify.NotifyBindingDTO;
import com.ifielsc.ijob.contract.schedule.ScheduleDTO;
import com.ifielsc.ijob.contract.task.TaskDetailDTO;
import com.ifielsc.ijob.contract.task.TaskListDTO;
import com.ifielsc.ijob.contract.task.TaskPageQuery;
import com.ifielsc.ijob.contract.task.TaskPublicationDTO;
import com.ifielsc.ijob.contract.task.TaskUpsertRequest;
import com.ifielsc.ijob.core.notify.NotifyBindingService;
import com.ifielsc.ijob.core.schedule.ScheduleService;
import com.ifielsc.ijob.core.task.definition.TaskDefinitionPublicationService;
import com.ifielsc.ijob.core.task.definition.TaskPublicationResult;
import com.ifielsc.ijob.core.task.retirement.TaskRetirementResult;
import com.ifielsc.ijob.core.task.retirement.TaskRetirementService;
import com.ifielsc.ijob.infra.entity.task.Task;
import com.ifielsc.ijob.infra.mapper.group.DefinitionGroupRow;
import com.ifielsc.ijob.infra.mapper.task.TaskDefinitionGroupMapper;
import com.ifielsc.ijob.infra.mapper.task.TaskMapper;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

class TaskServiceTest {
    @Test
    void groupCandidatesShouldNormalizeEscapeAndBoundPageSize() {
        RecordingTaskMapper mapper = new RecordingTaskMapper();
        TaskService service =
                service(
                        mapper,
                        new RecordingPublicationService(),
                        new RecordingRetirementService());
        var query = new com.ifielsc.ijob.contract.common.BasePageQuery();
        query.setPageSize(500);
        assertEquals(List.of("finance_ops"), service.queryGroups(query, " FINANCE_ ").getList());
        assertEquals("finance!_%", mapper.groupPattern);
        assertEquals(50, mapper.groupPageSize);
        org.junit.jupiter.api.Assertions.assertNull(
                com.github.pagehelper.PageHelper.getLocalPage());
        assertThrows(BusinessException.class, () -> service.queryGroups(query, "%"));
        assertThrows(BusinessException.class, () -> service.queryGroups(query, "a".repeat(65)));
    }

    @Test
    void createShouldPublishRevisionOneAndReturnContractDto() {
        RecordingPublicationService publications = new RecordingPublicationService();
        TaskService service =
                service(new RecordingTaskMapper(), publications, new RecordingRetirementService());
        TaskUpsertRequest request = validRequest();

        TaskPublicationDTO result = service.create(request);

        assertEquals(101L, result.taskId());
        assertEquals(201L, result.definitionId());
        assertEquals(1L, result.revision());
        assertSame(request, publications.createRequest);
    }

    @Test
    void updateShouldForwardExpectedRevisionAndReturnNextRevision() {
        RecordingPublicationService publications = new RecordingPublicationService();
        TaskService service =
                service(new RecordingTaskMapper(), publications, new RecordingRetirementService());
        TaskUpsertRequest request = validRequest();

        TaskPublicationDTO result = service.update(101L, 4L, request);

        assertEquals(101L, publications.publishTaskId);
        assertEquals(4L, publications.expectedRevision);
        assertSame(request, publications.publishRequest);
        assertEquals(5L, result.revision());
    }

    @Test
    void detailShouldExposeCurrentRevisionAndAssociations() {
        RecordingTaskMapper mapper = new RecordingTaskMapper();
        mapper.activeTask = storedTask(7L);
        RecordingScheduleService schedules = new RecordingScheduleService();
        ScheduleDTO schedule = new ScheduleDTO();
        schedule.setId(9L);
        schedules.details = List.of(schedule);
        RecordingNotifyBindingService bindings = new RecordingNotifyBindingService();
        NotifyBindingDTO binding = new NotifyBindingDTO();
        binding.setId(10L);
        bindings.details = List.of(binding);
        TaskService service =
                new TaskService(
                        mapper,
                        groups(),
                        schedules,
                        bindings,
                        new RecordingPublicationService(),
                        new RecordingRetirementService());

        TaskDetailDTO detail = service.getDetail(7L);

        assertEquals(7L, detail.getId());
        assertEquals(201L, detail.getCurrentTaskDefinitionId());
        assertEquals(3L, detail.getCurrentRevision());
        assertEquals(List.of(schedule), detail.getSchedules());
        assertEquals(List.of(binding), detail.getNotifyBindings());
        assertEquals(TargetType.TASK, schedules.lastTargetType);
    }

    @Test
    void detailShouldRejectInvalidOrInactiveTask() {
        TaskService service =
                service(
                        new RecordingTaskMapper(),
                        new RecordingPublicationService(),
                        new RecordingRetirementService());

        BusinessException invalid =
                assertThrows(BusinessException.class, () -> service.getDetail(0L));
        BusinessException missing =
                assertThrows(BusinessException.class, () -> service.getDetail(9L));

        assertEquals(ResultCode.PARAM_ERROR, invalid.getCode());
        assertEquals(ResultCode.NOT_FOUND, missing.getCode());
    }

    @Test
    void deleteShouldRetireWithoutDeletingAssociations() {
        RecordingRetirementService retirements = new RecordingRetirementService();
        RecordingScheduleService schedules = new RecordingScheduleService();
        RecordingNotifyBindingService bindings = new RecordingNotifyBindingService();
        TaskService service =
                new TaskService(
                        new RecordingTaskMapper(),
                        groups(),
                        schedules,
                        bindings,
                        new RecordingPublicationService(),
                        retirements);

        service.delete(8L);

        assertEquals(List.of(8L), retirements.taskIds);
        assertFalse(bindings.deleteCalled);
    }

    @Test
    void bulkDeleteShouldRetireIdsInAscendingOrder() throws Exception {
        RecordingRetirementService retirements = new RecordingRetirementService();
        TaskService service =
                service(new RecordingTaskMapper(), new RecordingPublicationService(), retirements);

        BatchDeleteRequest request = new BatchDeleteRequest();
        request.setIds(List.of(9L, 3L, 5L));
        service.deleteBatch(request);

        assertEquals(List.of(3L, 5L, 9L), retirements.taskIds);
        Method method = TaskService.class.getMethod("deleteBatch", BatchDeleteRequest.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertFalse(transactional.readOnly());
    }

    private TaskService service(
            TaskMapper mapper,
            TaskDefinitionPublicationService publications,
            TaskRetirementService retirements) {
        return new TaskService(
                mapper,
                groups(),
                new RecordingScheduleService(),
                new RecordingNotifyBindingService(),
                publications,
                retirements);
    }

    private static TaskDefinitionGroupMapper groups() {
        return new TaskDefinitionGroupMapper() {
            @Override
            public int insert(Long definitionId, String groupCode, LocalDateTime createTime) {
                return 1;
            }

            @Override
            public List<DefinitionGroupRow> selectByDefinitionIds(List<Long> definitionIds) {
                return List.of();
            }
        };
    }

    private TaskUpsertRequest validRequest() {
        TaskUpsertRequest request = new TaskUpsertRequest();
        request.setName("demo");
        request.setPluginType(PluginType.EVENT);
        request.setPluginConfig("{}");
        return request;
    }

    private Task storedTask(Long id) {
        Task task = new Task();
        task.setId(id);
        task.setName("stored");
        task.setPluginType(PluginType.EVENT);
        task.setPluginConfig("{}");
        task.setRetryTimes(0);
        task.setRetryInterval(0);
        task.setEnabled(true);
        task.setCurrentTaskDefinitionId(201L);
        task.setCurrentRevision(3L);
        task.setRetired(false);
        return task;
    }

    private static final class RecordingTaskMapper implements TaskMapper {
        private Task activeTask;
        private String groupPattern;
        private int groupPageSize;

        @Override
        public List<String> selectGroupCodes(String pattern) {
            groupPattern = pattern;
            groupPageSize = com.github.pagehelper.PageHelper.getLocalPage().getPageSize();
            return List.of("finance_ops");
        }

        @Override
        public List<TaskListDTO> selectDtoList(TaskPageQuery query) {
            return List.of();
        }

        @Override
        public Task selectById(Long id) {
            return activeTask != null && id.equals(activeTask.getId()) ? activeTask : null;
        }
    }

    private static final class RecordingPublicationService
            extends TaskDefinitionPublicationService {
        private TaskUpsertRequest createRequest;
        private Long publishTaskId;
        private Long expectedRevision;
        private TaskUpsertRequest publishRequest;

        private RecordingPublicationService() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public TaskPublicationResult create(TaskUpsertRequest request) {
            createRequest = request;
            return new TaskPublicationResult(101L, 201L, 1L);
        }

        @Override
        public TaskPublicationResult publish(
                Long taskId, Long expected, TaskUpsertRequest request) {
            publishTaskId = taskId;
            expectedRevision = expected;
            publishRequest = request;
            return new TaskPublicationResult(taskId, 205L, expected + 1);
        }
    }

    private static final class RecordingRetirementService extends TaskRetirementService {
        private final List<Long> taskIds = new ArrayList<>();

        private RecordingRetirementService() {
            super(null, null, null, null);
        }

        @Override
        public TaskRetirementResult retire(Long taskId) {
            taskIds.add(taskId);
            return new TaskRetirementResult(taskId, LocalDateTime.now(), false);
        }

        @Override
        public List<TaskRetirementResult> retireBatch(List<Long> ids) {
            taskIds.addAll(ids);
            return ids.stream()
                    .map(id -> new TaskRetirementResult(id, LocalDateTime.now(), false))
                    .toList();
        }
    }

    private static final class RecordingScheduleService extends ScheduleService {
        private List<ScheduleDTO> details = List.of();
        private TargetType lastTargetType;

        private RecordingScheduleService() {
            super(null, null, null, null, null);
        }

        @Override
        public List<ScheduleDTO> listScheduleDetailsByTargetId(
                TargetType targetType, Long targetId) {
            lastTargetType = targetType;
            return details;
        }
    }

    private static final class RecordingNotifyBindingService extends NotifyBindingService {
        private List<NotifyBindingDTO> details = List.of();
        private boolean deleteCalled;

        private RecordingNotifyBindingService() {
            super(null, null, null, null);
        }

        @Override
        public List<NotifyBindingDTO> listDetailsByTarget(TargetType targetType, Long targetId) {
            return details;
        }

        @Override
        public void deleteBindingsByTarget(TargetType targetType, Long targetId) {
            deleteCalled = true;
        }
    }
}
