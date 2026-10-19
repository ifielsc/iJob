package com.ifielsc.ijob.api.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.contract.runtime.ManualTriggerRequest;
import com.ifielsc.ijob.core.trigger.ManualTaskTriggerService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;

class TaskExecutionControllerTest {
    @Test
    void shouldReturnAcceptedTriggerResource() {
        RecordingService service = new RecordingService();
        TaskExecutionController controller = new TaskExecutionController(service);

        ResponseEntity<ApiResponse<String>> response = controller.trigger(12L, null);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals(URI.create("/api/triggers/81"), response.getHeaders().getLocation());
        assertTrue(response.getBody().isSuccess());
        assertEquals("81", response.getBody().getData());
        assertEquals(12L, service.taskId);
    }

    private static final class RecordingService extends ManualTaskTriggerService {
        private Long taskId;

        private RecordingService() {
            super(null, null, null, null);
        }

        @Override
        public Long trigger(Long taskId, ManualTriggerRequest request) {
            this.taskId = taskId;
            return 81L;
        }
    }
}
