package com.ifielsc.ijob.core.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.runtime.ApplicationRuntimeState;
import com.ifielsc.ijob.contract.enums.NotifyType;
import com.ifielsc.ijob.infra.coordination.notification.NotificationOutboxItem;
import com.ifielsc.ijob.infra.entity.notify.NotifyConfig;

import org.junit.jupiter.api.Test;

import java.util.List;

class NotificationOutboxScannerTest {
    @Test
    void readyWorkerShouldDispatchSnapshotAndCompleteClaim() {
        AppProperties properties = new AppProperties();
        ApplicationRuntimeState runtimeState = new ApplicationRuntimeState();
        runtimeState.markReady();
        RecordingOutboxService outbox = new RecordingOutboxService(properties);
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        NotificationOutboxScanner scanner =
                new NotificationOutboxScanner(outbox, dispatcher, runtimeState, properties);

        try {
            scanner.scan();

            assertEquals("ifjenc:v1:snapshot", dispatcher.config.getConfig());
            assertTrue(outbox.completed);
            assertFalse(outbox.retried);
        } finally {
            scanner.destroy();
        }
    }

    @Test
    void unreadyRuntimeShouldLeaveOutboxUntouched() {
        AppProperties properties = new AppProperties();
        RecordingOutboxService outbox = new RecordingOutboxService(properties);
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        NotificationOutboxScanner scanner =
                new NotificationOutboxScanner(
                        outbox, dispatcher, new ApplicationRuntimeState(), properties);

        try {
            scanner.scan();

            assertEquals(0, outbox.listCalls);
            assertEquals(0, dispatcher.calls);
        } finally {
            scanner.destroy();
        }
    }

    private static final class RecordingOutboxService extends NotificationOutboxService {
        private final NotificationOutboxItem item = new NotificationOutboxItem();
        private int listCalls;
        private boolean completed;
        private boolean retried;

        private RecordingOutboxService(AppProperties properties) {
            super(null, properties);
            item.setId(91L);
            item.setNotifyConfigId(92L);
            item.setNotifyType(NotifyType.EMAIL);
            item.setConfigSnapshot("ifjenc:v1:snapshot");
            item.setConfigEnabled(Boolean.TRUE);
            item.setSubject("subject");
            item.setContent("content");
        }

        @Override
        public List<Long> listDispatchableIds() {
            listCalls++;
            return List.of(item.getId());
        }

        @Override
        public NotificationOutboxItem claim(Long id, String leaseOwner) {
            item.setLeaseOwner(leaseOwner);
            item.setClaimToken("claim");
            return item;
        }

        @Override
        public boolean complete(NotificationOutboxItem claimed) {
            completed = true;
            return true;
        }

        @Override
        public boolean retry(NotificationOutboxItem claimed, NotifyDispatchStatus status) {
            retried = true;
            return true;
        }
    }

    private static final class RecordingDispatcher extends NotifyDispatcher {
        private NotifyConfig config;
        private int calls;

        private RecordingDispatcher() {
            super(null, List.of(), null);
        }

        @Override
        public NotifyDispatchResult dispatch(
                NotifyConfig configSnapshot, NotifyMessage message, NotifyDispatchContext context) {
            config = configSnapshot;
            calls++;
            return new NotifyDispatchResult(
                    NotifyDispatchStatus.DELIVERED, configSnapshot.getType());
        }
    }
}
