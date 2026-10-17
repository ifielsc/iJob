package com.ifelio.ijob.api.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.contract.auth.CurrentUserResponse;
import com.ifelio.ijob.contract.auth.LoginResponse;
import com.ifelio.ijob.contract.enums.RuntimeCommandType;
import com.ifelio.ijob.contract.runtime.RuntimeCommandDTO;
import com.ifelio.ijob.core.auth.AuthSession;
import com.ifelio.ijob.core.execute.RuntimeCommandService;

import org.junit.jupiter.api.Test;

class InstanceControlControllerTest {
    @Test
    void shouldDelegateAllInstanceCommands() {
        RecordingRuntimeService service = new RecordingRuntimeService();
        InstanceControlController controller =
                new InstanceControlController(service, new RecordingAuthSession());

        ApiResponse<RuntimeCommandDTO> stopped = controller.stop(1L, "stop-request");
        ApiResponse<RuntimeCommandDTO> paused = controller.pause(2L, "pause-request");
        ApiResponse<RuntimeCommandDTO> resumed = controller.resume(3L, "resume-request");
        ApiResponse<RuntimeCommandDTO> retried = controller.retry(4L, "retry-request");

        assertTrue(stopped.isSuccess());
        assertTrue(paused.isSuccess());
        assertTrue(resumed.isSuccess());
        assertTrue(retried.isSuccess());
        assertEquals(1L, service.ids.get(RuntimeCommandType.STOP));
        assertEquals(2L, service.ids.get(RuntimeCommandType.PAUSE));
        assertEquals(3L, service.ids.get(RuntimeCommandType.RESUME));
        assertEquals(4L, service.ids.get(RuntimeCommandType.RETRY));
        assertEquals("operator", service.requestedBy);
        assertEquals("stop-request", stopped.getData().getRequestId());
    }

    private static final class RecordingRuntimeService extends RuntimeCommandService {
        private final java.util.Map<RuntimeCommandType, Long> ids =
                new java.util.EnumMap<>(RuntimeCommandType.class);
        private String requestedBy;

        private RecordingRuntimeService() {
            super(null, null, null);
        }

        @Override
        public RuntimeCommandDTO submit(
                Long id, RuntimeCommandType commandType, String requestId, String requestedBy) {
            ids.put(commandType, id);
            this.requestedBy = requestedBy;
            RuntimeCommandDTO result = new RuntimeCommandDTO();
            result.setRequestId(requestId);
            result.setInstanceId(id);
            result.setCommandType(commandType);
            return result;
        }
    }

    private static final class RecordingAuthSession implements AuthSession {
        @Override
        public LoginResponse login(String username) {
            return null;
        }

        @Override
        public CurrentUserResponse currentUser() {
            return new CurrentUserResponse("operator", "Operator");
        }

        @Override
        public void logout() {}
    }
}
