package com.ifelio.ijob.api.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.common.response.PageResult;
import com.ifelio.ijob.contract.task.TaskDetailDTO;
import com.ifelio.ijob.contract.task.TaskListDTO;
import com.ifelio.ijob.contract.task.TaskPageQuery;
import com.ifelio.ijob.contract.task.TaskPublicationDTO;
import com.ifelio.ijob.contract.task.TaskUpsertRequest;
import com.ifelio.ijob.core.task.TaskService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.List;

class TaskControllerTest {
    @Test
    void createShouldReturnPublicationContractAndLocation() {
        RecordingTaskService service = new RecordingTaskService();
        TaskController controller = new TaskController(service);
        TaskUpsertRequest request = new TaskUpsertRequest();

        ResponseEntity<ApiResponse<TaskPublicationDTO>> response = controller.create(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(URI.create("/api/tasks/88"), response.getHeaders().getLocation());
        assertTrue(response.getBody().isSuccess());
        assertEquals(88L, response.getBody().getData().taskId());
        assertEquals(1L, response.getBody().getData().revision());
        assertSame(request, service.createRequest);
    }

    @Test
    void queryAndDetailShouldWrapContractDtos() {
        RecordingTaskService service = new RecordingTaskService();
        TaskController controller = new TaskController(service);
        TaskPageQuery query = new TaskPageQuery();

        ApiResponse<PageResult<TaskListDTO>> page = controller.queryPage(query);
        ApiResponse<TaskDetailDTO> detail = controller.getDetail(9L);

        assertTrue(page.isSuccess());
        assertSame(service.pageResult, page.getData());
        assertSame(query, service.pageQuery);
        assertTrue(detail.isSuccess());
        assertSame(service.detail, detail.getData());
        assertEquals(9L, service.detailId);
    }

    @Test
    void updateShouldRequireExpectedRevisionAndReturnPublication() {
        RecordingTaskService service = new RecordingTaskService();
        TaskController controller = new TaskController(service);
        TaskUpsertRequest request = new TaskUpsertRequest();

        ApiResponse<TaskPublicationDTO> update = controller.update(12L, 4L, request);

        assertTrue(update.isSuccess());
        assertEquals(12L, service.updateId);
        assertEquals(4L, service.expectedRevision);
        assertSame(request, service.updateRequest);
        assertEquals(5L, update.getData().revision());
    }

    @Test
    void deleteShouldRetirePathId() {
        RecordingTaskService service = new RecordingTaskService();
        TaskController controller = new TaskController(service);

        ApiResponse<Void> delete = controller.delete(13L);

        assertTrue(delete.isSuccess());
        assertEquals(13L, service.deleteId);
    }

    private static final class RecordingTaskService extends TaskService {
        private final PageResult<TaskListDTO> pageResult = pageResult();
        private final TaskDetailDTO detail = new TaskDetailDTO();
        private TaskPageQuery pageQuery;
        private Long detailId;
        private TaskUpsertRequest createRequest;
        private Long updateId;
        private Long expectedRevision;
        private TaskUpsertRequest updateRequest;
        private Long deleteId;

        private RecordingTaskService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public PageResult<TaskListDTO> queryPage(TaskPageQuery query) {
            pageQuery = query;
            return pageResult;
        }

        @Override
        public TaskDetailDTO getDetail(Long id) {
            detailId = id;
            return detail;
        }

        @Override
        public TaskPublicationDTO create(TaskUpsertRequest request) {
            createRequest = request;
            return new TaskPublicationDTO(88L, 188L, 1L);
        }

        @Override
        public TaskPublicationDTO update(Long taskId, Long expected, TaskUpsertRequest request) {
            updateId = taskId;
            expectedRevision = expected;
            updateRequest = request;
            return new TaskPublicationDTO(taskId, 189L, expected + 1);
        }

        @Override
        public void delete(Long id) {
            deleteId = id;
        }

        private static PageResult<TaskListDTO> pageResult() {
            PageResult<TaskListDTO> result = new PageResult<>();
            result.setList(List.of());
            result.setPageNum(1);
            result.setPageSize(10);
            return result;
        }
    }
}
