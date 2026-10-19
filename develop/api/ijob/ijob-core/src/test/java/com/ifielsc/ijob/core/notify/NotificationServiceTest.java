// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.ifielsc.ijob.contract.enums.NotifySendSource;
import com.ifielsc.ijob.infra.coordination.notification.NotificationOutboxItem;

import org.junit.jupiter.api.Test;

class NotificationServiceTest {
    @Test
    void directMessageSendShouldDelegateAndReturnResult() {
        RecordingOutboxService outbox = new RecordingOutboxService();
        NotificationService service = new NotificationService(outbox, null, null);
        NotifyMessage message = NotifyMessage.builder().subject("monitor").content("alarm").build();

        NotifyDispatchResult result = service.send(51L, message);

        assertEquals(NotifyDispatchStatus.QUEUED, result.status());
        assertNull(result.type());
        assertEquals(51L, outbox.notifyConfigId);
        assertSame(message, outbox.message);
        assertEquals(NotifySendSource.SYSTEM, outbox.context.getSourceType());
    }

    @Test
    void invalidTemplateContextShouldFailWithoutDispatch() {
        RecordingOutboxService outbox = new RecordingOutboxService();
        NotificationService service = new NotificationService(outbox, null, null);

        NotifyDispatchResult result = service.send(52L, (NotificationContext) null);

        assertEquals(NotifyDispatchStatus.DELIVERY_FAILED, result.status());
        assertNull(outbox.notifyConfigId);
    }

    private static final class RecordingOutboxService extends NotificationOutboxService {
        private Long notifyConfigId;
        private NotifyMessage message;
        private NotifyDispatchContext context;

        private RecordingOutboxService() {
            super(null, null);
        }

        @Override
        public NotificationOutboxItem enqueue(
                Long dispatchedConfigId,
                NotifyMessage dispatchedMessage,
                NotifyDispatchContext dispatchedContext) {
            notifyConfigId = dispatchedConfigId;
            message = dispatchedMessage;
            context = dispatchedContext;
            NotificationOutboxItem item = new NotificationOutboxItem();
            item.setId(1L);
            return item;
        }
    }
}
