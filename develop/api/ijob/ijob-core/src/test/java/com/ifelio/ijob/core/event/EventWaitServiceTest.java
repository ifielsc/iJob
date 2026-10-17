package com.ifelio.ijob.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.contract.enums.EventSourceType;
import com.ifelio.ijob.contract.enums.EventWaitStatus;
import com.ifelio.ijob.contract.enums.EventWaitType;
import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.core.instance.InstanceService;
import com.ifelio.ijob.infra.entity.runtime.EventRecord;
import com.ifelio.ijob.infra.entity.runtime.EventWait;
import com.ifelio.ijob.infra.entity.runtime.Instance;
import com.ifelio.ijob.infra.mapper.runtime.EventRecordMapper;
import com.ifelio.ijob.infra.mapper.runtime.EventWaitMapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class EventWaitServiceTest {
    @Test
    void exactInboundEventShouldMatchOutsideDefaultLookback() {
        EventRecord inbound = event(99L, "event-a");
        AtomicReference<EventWait> inserted = new AtomicReference<>();
        EventWaitMapper waitMapper = waitMapper(null, inserted);
        EventRecordMapper recordMapper = recordMapper(inbound);
        StubInstanceService instances = new StubInstanceService(running());
        EventWaitService service =
                new EventWaitService(waitMapper, recordMapper, instances, new ObjectMapper());

        EventWaitRegistrationResult result =
                service.register(
                        1L,
                        new EventWaitRegistration(
                                EventWaitType.EVENT, "event-a", null, null, 60, 0, null, 99L));

        assertEquals(EventWaitStatus.MATCHED, result.status());
        assertEquals(99L, result.matchedEventId());
        assertEquals(EventWaitStatus.MATCHED, inserted.get().getStatus());
        assertFalse(instances.waitingCalled);
    }

    @Test
    void existingRejectedApprovalShouldPreserveRejectedStatus() {
        EventWait existing = new EventWait();
        existing.setStatus(EventWaitStatus.REJECTED);
        Instance terminal = running();
        terminal.setStatus(InstanceStatus.FAILED);
        StubInstanceService instances = new StubInstanceService(terminal);
        EventWaitService service =
                new EventWaitService(
                        waitMapper(existing, new AtomicReference<>()),
                        recordMapper(null),
                        instances,
                        new ObjectMapper());

        EventWaitRegistrationResult result =
                service.register(
                        1L,
                        new EventWaitRegistration(
                                EventWaitType.APPROVAL,
                                null,
                                null,
                                null,
                                null,
                                0,
                                List.of("reviewer")));

        assertEquals(EventWaitStatus.REJECTED, result.status());
    }

    @Test
    void approvalRegistrationShouldDualWriteDistinctOperatorSnapshots() {
        AtomicReference<EventWait> inserted = new AtomicReference<>();
        List<String> insertedOperators = new ArrayList<>();
        EventWaitMapper waitMapper =
                proxy(
                        EventWaitMapper.class,
                        (method, arguments) ->
                                switch (method) {
                                    case "selectByInstanceAndAttempt" -> null;
                                    case "insert" -> {
                                        EventWait wait = (EventWait) arguments[0];
                                        wait.setId(41L);
                                        inserted.set(wait);
                                        yield 1;
                                    }
                                    case "insertOperator" -> {
                                        assertEquals(41L, arguments[0]);
                                        insertedOperators.add((String) arguments[1]);
                                        yield 1;
                                    }
                                    default -> defaultValue(method);
                                });
        EventWaitService service =
                new EventWaitService(
                        waitMapper,
                        recordMapper(null),
                        new StubInstanceService(running()),
                        new ObjectMapper());

        service.register(
                1L,
                new EventWaitRegistration(
                        EventWaitType.APPROVAL,
                        null,
                        null,
                        null,
                        60,
                        0,
                        List.of("reviewer", "owner", "reviewer")));

        assertEquals("[\"reviewer\",\"owner\"]", inserted.get().getAllowedOperatorIds());
        assertIterableEquals(List.of("reviewer", "owner"), insertedOperators);
    }

    private EventWaitMapper waitMapper(EventWait existing, AtomicReference<EventWait> inserted) {
        return proxy(
                EventWaitMapper.class,
                (method, arguments) ->
                        switch (method) {
                            case "selectByInstanceAndAttempt" -> existing;
                            case "insert" -> {
                                EventWait wait = (EventWait) arguments[0];
                                wait.setId(1L);
                                inserted.set(wait);
                                yield 1;
                            }
                            case "insertOperator" -> 1;
                            default -> defaultValue(method);
                        });
    }

    private EventRecordMapper recordMapper(EventRecord inbound) {
        return proxy(
                EventRecordMapper.class,
                (method, arguments) ->
                        switch (method) {
                            case "selectById" -> inbound;
                            case "selectFirstMatching" -> null;
                            default -> defaultValue(method);
                        });
    }

    private Instance running() {
        Instance instance = new Instance();
        instance.setId(1L);
        instance.setRootInstanceId(1L);
        instance.setRetryCount(0);
        instance.setStatus(InstanceStatus.RUNNING);
        return instance;
    }

    private EventRecord event(Long id, String code) {
        EventRecord event = new EventRecord();
        event.setId(id);
        event.setEventCode(code);
        event.setSourceType(EventSourceType.HTTP);
        event.setArrivedAt(LocalDateTime.now().minusMinutes(1));
        return event;
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Invocation invocation) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (proxy, method, arguments) ->
                                invocation.invoke(method.getName(), arguments));
    }

    private Object defaultValue(String method) {
        if (method.startsWith("select")) {
            return List.of();
        }
        return 0;
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] arguments) throws Exception;
    }

    private static final class StubInstanceService extends InstanceService {
        private final Instance instance;
        private boolean waitingCalled;

        private StubInstanceService(Instance instance) {
            super(null, null);
            this.instance = instance;
        }

        @Override
        public Instance getInstance(Long id) {
            return instance;
        }

        @Override
        public void waitingEvent(Long instanceId) {
            waitingCalled = true;
        }
    }
}
