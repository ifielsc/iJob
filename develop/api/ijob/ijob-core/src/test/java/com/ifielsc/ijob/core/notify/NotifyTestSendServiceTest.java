package com.ifielsc.ijob.core.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.NotifySendSource;
import com.ifielsc.ijob.contract.enums.NotifyTestSendStatus;
import com.ifielsc.ijob.contract.enums.NotifyType;
import com.ifielsc.ijob.contract.notify.NotifyTestSendDTO;
import com.ifielsc.ijob.contract.notify.NotifyTestSendRequest;

import org.junit.jupiter.api.Test;

import java.util.List;

class NotifyTestSendServiceTest {
    @Test
    void testSendShouldReturnFixedDeliveredDto() {
        StubNotifyDispatcher dispatcher = new StubNotifyDispatcher();
        dispatcher.result =
                new NotifyDispatchResult(NotifyDispatchStatus.DELIVERED, NotifyType.EMAIL);
        NotifyTestSendService service = new NotifyTestSendService(dispatcher);
        NotifyTestSendRequest request = request("  probe  ", "body");

        NotifyTestSendDTO result = service.testSend(31L, request, "operator-31");

        assertEquals(31L, result.getNotifyConfigId());
        assertEquals(NotifyType.EMAIL, result.getType());
        assertEquals(NotifyTestSendStatus.DELIVERED, result.getStatus());
        assertEquals(31L, dispatcher.notifyConfigId);
        assertEquals("probe", dispatcher.message.getSubject());
        assertEquals("body", dispatcher.message.getContent());
        assertEquals(NotifySendSource.TEST_SEND, dispatcher.context.getSourceType());
        assertEquals("operator-31", dispatcher.context.getOperatorId());
        assertFalse(request.toString().contains("probe"));
        assertFalse(request.toString().contains("body"));
    }

    @Test
    void testSendShouldRejectInvalidInputBeforeDispatch() {
        StubNotifyDispatcher dispatcher = new StubNotifyDispatcher();
        NotifyTestSendService service = new NotifyTestSendService(dispatcher);

        assertParamError(() -> service.testSend(0L, request("s", "c")));
        assertParamError(() -> service.testSend(1L, null));
        assertParamError(() -> service.testSend(1L, request(" ", "c")));
        assertParamError(() -> service.testSend(1L, request("s", " ")));
        assertParamError(() -> service.testSend(1L, request("s".repeat(256), "c")));
        assertParamError(() -> service.testSend(1L, request("s", "c".repeat(10_001))));
        assertNull(dispatcher.notifyConfigId);
    }

    @Test
    void testSendShouldMapUnavailableStatesToFixedBusinessErrors() {
        StubNotifyDispatcher dispatcher = new StubNotifyDispatcher();
        NotifyTestSendService service = new NotifyTestSendService(dispatcher);

        assertBusinessError(
                service,
                dispatcher,
                NotifyDispatchStatus.CONFIG_NOT_FOUND,
                ResultCode.NOT_FOUND,
                "notify config does not exist");
        assertBusinessError(
                service,
                dispatcher,
                NotifyDispatchStatus.CONFIG_DISABLED,
                ResultCode.CONFLICT,
                "notify config is disabled");
        assertBusinessError(
                service,
                dispatcher,
                NotifyDispatchStatus.CHANNEL_UNAVAILABLE,
                ResultCode.SERVICE_UNAVAILABLE,
                "notify channel is unavailable");
        assertBusinessError(
                service,
                dispatcher,
                NotifyDispatchStatus.DELIVERY_FAILED,
                ResultCode.SERVICE_UNAVAILABLE,
                "notify test send failed");
    }

    private void assertBusinessError(
            NotifyTestSendService service,
            StubNotifyDispatcher dispatcher,
            NotifyDispatchStatus status,
            ResultCode code,
            String message) {
        dispatcher.result = new NotifyDispatchResult(status, NotifyType.EMAIL);

        BusinessException error =
                assertThrows(
                        BusinessException.class,
                        () -> service.testSend(41L, request("subject", "content")));

        assertEquals(code, error.getCode());
        assertEquals(message, error.getMessage());
    }

    private void assertParamError(Runnable action) {
        BusinessException error = assertThrows(BusinessException.class, action::run);
        assertEquals(ResultCode.PARAM_ERROR, error.getCode());
    }

    private NotifyTestSendRequest request(String subject, String content) {
        NotifyTestSendRequest request = new NotifyTestSendRequest();
        request.setSubject(subject);
        request.setContent(content);
        return request;
    }

    private static final class StubNotifyDispatcher extends NotifyDispatcher {
        private NotifyDispatchResult result;
        private Long notifyConfigId;
        private NotifyMessage message;
        private NotifyDispatchContext context;

        private StubNotifyDispatcher() {
            super(null, List.of(), null);
        }

        @Override
        public NotifyDispatchResult dispatch(
                Long dispatchedConfigId,
                NotifyMessage dispatchedMessage,
                NotifyDispatchContext dispatchedContext) {
            notifyConfigId = dispatchedConfigId;
            message = dispatchedMessage;
            context = dispatchedContext;
            return result;
        }
    }
}
