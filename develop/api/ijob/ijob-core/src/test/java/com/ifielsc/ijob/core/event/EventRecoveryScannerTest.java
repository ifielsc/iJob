// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifielsc.ijob.common.runtime.ApplicationRuntimeState;
import com.ifielsc.ijob.infra.entity.runtime.EventRecord;
import com.ifielsc.ijob.infra.entity.runtime.EventWait;
import com.ifielsc.ijob.infra.mapper.runtime.EventRecordMapper;
import com.ifielsc.ijob.infra.mapper.runtime.EventWaitMapper;
import com.ifielsc.ijob.infra.mapper.runtime.TriggerMapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

class EventRecoveryScannerTest {
    @Test
    void failedWaitShouldNotBlockLaterRecoveryItems() {
        EventWait first = wait(1L, "first");
        EventWait second = wait(2L, "second");
        RecordingResolutionService resolution = new RecordingResolutionService();
        ApplicationRuntimeState runtimeState = new ApplicationRuntimeState();
        runtimeState.markReady();
        EventRecoveryScanner scanner =
                new EventRecoveryScanner(
                        triggerMapper(),
                        null,
                        waitMapper(List.of(first, second)),
                        recordMapper(),
                        resolution,
                        null,
                        runtimeState);

        scanner.scan();

        assertEquals(2, resolution.calls);
    }

    @Test
    void resolvedContinuationShouldUseGuardedDispatcherPath() {
        RecordingContinuationDispatcher dispatcher = new RecordingContinuationDispatcher();
        ApplicationRuntimeState runtimeState = new ApplicationRuntimeState();
        runtimeState.markReady();
        EventRecoveryScanner scanner =
                new EventRecoveryScanner(
                        triggerMapper(),
                        null,
                        waitMapper(List.of(), List.of(7L)),
                        recordMapper(),
                        new RecordingResolutionService(),
                        dispatcher,
                        runtimeState);

        scanner.scan();

        assertEquals(1, dispatcher.calls);
    }

    private TriggerMapper triggerMapper() {
        return (TriggerMapper)
                Proxy.newProxyInstance(
                        TriggerMapper.class.getClassLoader(),
                        new Class<?>[] {TriggerMapper.class},
                        (proxy, method, arguments) -> {
                            if ("selectWaitingIds".equals(method.getName())) {
                                return List.of();
                            }
                            throw new UnsupportedOperationException(method.getName());
                        });
    }

    private EventWaitMapper waitMapper(List<EventWait> waits) {
        return waitMapper(waits, List.of());
    }

    private EventWaitMapper waitMapper(List<EventWait> waits, List<Long> resolvedRootIds) {
        return (EventWaitMapper)
                Proxy.newProxyInstance(
                        EventWaitMapper.class.getClassLoader(),
                        new Class<?>[] {EventWaitMapper.class},
                        (proxy, method, arguments) ->
                                switch (method.getName()) {
                                    case "selectRecoverableWaitingBatch" -> waits;
                                    case "selectResolvedRootIds" -> resolvedRootIds;
                                    case "selectTimedOut" -> List.of();
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                });
    }

    private EventRecordMapper recordMapper() {
        return (EventRecordMapper)
                Proxy.newProxyInstance(
                        EventRecordMapper.class.getClassLoader(),
                        new Class<?>[] {EventRecordMapper.class},
                        (proxy, method, arguments) -> {
                            if ("selectFirstMatching".equals(method.getName())) {
                                EventRecord event = new EventRecord();
                                event.setId("first".equals(arguments[0]) ? 1L : 2L);
                                return event;
                            }
                            throw new UnsupportedOperationException(method.getName());
                        });
    }

    private EventWait wait(Long id, String eventCode) {
        EventWait wait = new EventWait();
        wait.setId(id);
        wait.setEventCode(eventCode);
        return wait;
    }

    private static final class RecordingResolutionService extends EventWaitResolutionService {
        private int calls;

        private RecordingResolutionService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public int match(EventRecord event) {
            calls++;
            if (event.getId() == 1L) {
                throw new IllegalStateException("broken wait");
            }
            return 1;
        }
    }

    private static final class RecordingContinuationDispatcher
            extends InstanceContinuationDispatcher {
        private int calls;

        private RecordingContinuationDispatcher() {
            super(null, null, null, null);
        }

        @Override
        public void dispatchAfterCommit(Long rootInstanceId) {
            calls++;
        }
    }
}
