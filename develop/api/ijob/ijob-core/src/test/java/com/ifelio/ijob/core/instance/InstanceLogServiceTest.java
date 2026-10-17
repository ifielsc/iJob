package com.ifelio.ijob.core.instance;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifelio.ijob.infra.entity.runtime.InstanceLog;
import com.ifelio.ijob.infra.mapper.runtime.InstanceLogMapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

class InstanceLogServiceTest {
    @Test
    void storesInstanceAndDiagnosticWithoutChangingText() {
        var saved = new AtomicReference<InstanceLog>();
        var mapper =
                (InstanceLogMapper)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {InstanceLogMapper.class},
                                (proxy, method, args) -> {
                                    saved.set((InstanceLog) args[0]);
                                    return 1;
                                });
        new InstanceLogService(mapper).info(7L, "SQL transaction committed");
        assertEquals(7L, saved.get().getInstanceId());
        assertEquals("SQL transaction committed", saved.get().getContent());
    }

    @Test
    void diagnosticStoreFailureDoesNotRetryBusinessWork() {
        var mapper =
                (InstanceLogMapper)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {InstanceLogMapper.class},
                                (proxy, method, args) -> {
                                    throw new IllegalStateException("database unavailable");
                                });
        assertDoesNotThrow(
                () -> new InstanceLogService(mapper).info(7L, "SQL transaction committed"));
        var service = new InstanceLogService(mapper);
        service.setObserver(
                new com.ifelio.ijob.common.observability.CoordinationObserver() {
                    @Override
                    public void recordEvent(String component, String event) {
                        assertEquals("log", component);
                        assertEquals("dropped", event);
                        throw new IllegalStateException("metrics unavailable");
                    }
                });
        assertDoesNotThrow(() -> service.info(7L, "SQL transaction committed"));
    }
}
