package com.ifelio.ijob.api.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.auth.CurrentUserResponse;
import com.ifelio.ijob.contract.auth.LoginResponse;
import com.ifelio.ijob.contract.event.EventPublishRequest;
import com.ifelio.ijob.contract.event.EventReceiptDTO;
import com.ifelio.ijob.core.auth.AuthSession;
import com.ifelio.ijob.core.event.EventPublishCommand;
import com.ifelio.ijob.core.event.EventReceiptResult;
import com.ifelio.ijob.core.event.EventReceiptService;

import org.junit.jupiter.api.Test;

class EventIngressControllerTest {
    @Test
    void headerOnlyIdempotencyKeyShouldBeNormalizedAndPersisted() {
        RecordingEventReceiptService service = new RecordingEventReceiptService();
        EventIngressController controller =
                new EventIngressController(service, new RecordingAuthSession());
        EventPublishRequest request = request(null);

        ApiResponse<EventReceiptDTO> response = controller.publish(request, " event-1 ");

        assertTrue(response.isSuccess());
        assertEquals("event-1", service.command.idempotencyKey());
        assertEquals("HTTP:operator:source-a", service.command.sourceKey());
    }

    @Test
    void matchingHeaderAndBodyShouldUseOneIdempotencyKey() {
        RecordingEventReceiptService service = new RecordingEventReceiptService();
        EventIngressController controller =
                new EventIngressController(service, new RecordingAuthSession());

        controller.publish(request("event-2"), " event-2 ");

        assertEquals("event-2", service.command.idempotencyKey());
    }

    @Test
    void conflictingHeaderAndBodyShouldBeRejectedBeforePersistence() {
        RecordingEventReceiptService service = new RecordingEventReceiptService();
        EventIngressController controller =
                new EventIngressController(service, new RecordingAuthSession());

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> controller.publish(request("body-key"), "header-key"));

        assertEquals(ResultCode.CONFLICT, exception.getCode());
        assertEquals(0, service.calls);
    }

    @Test
    void missingIdempotencyKeyShouldBeRejectedBeforePersistence() {
        RecordingEventReceiptService service = new RecordingEventReceiptService();
        EventIngressController controller =
                new EventIngressController(service, new RecordingAuthSession());

        BusinessException exception =
                assertThrows(
                        BusinessException.class, () -> controller.publish(request(null), null));

        assertEquals(ResultCode.PARAM_ERROR, exception.getCode());
        assertEquals(0, service.calls);
    }

    private EventPublishRequest request(String idempotencyKey) {
        EventPublishRequest request = new EventPublishRequest();
        request.setSourceKey("source-a");
        request.setEventCode("order.created");
        request.setIdempotencyKey(idempotencyKey);
        return request;
    }

    private static final class RecordingEventReceiptService extends EventReceiptService {
        private EventPublishCommand command;
        private int calls;

        private RecordingEventReceiptService() {
            super(null, null, null, null, new com.fasterxml.jackson.databind.ObjectMapper());
        }

        @Override
        public EventReceiptResult receive(EventPublishCommand requested) {
            command = requested;
            calls++;
            return new EventReceiptResult(41L, false);
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
