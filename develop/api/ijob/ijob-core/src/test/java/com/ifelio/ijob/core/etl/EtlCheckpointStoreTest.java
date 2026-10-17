package com.ifelio.ijob.core.etl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.infra.entity.runtime.EtlCheckpoint;
import com.ifelio.ijob.infra.entity.runtime.Instance;
import com.ifelio.ijob.infra.mapper.runtime.EtlCheckpointMapper;
import com.ifelio.ijob.infra.mapper.runtime.InstanceMapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class EtlCheckpointStoreTest {
    @Test
    void shouldInitializeAndAdvanceCompositeCheckpoint() {
        AtomicReference<EtlCheckpoint> stored = new AtomicReference<>();
        EtlCheckpointStore store =
                new EtlCheckpointStore(
                        checkpointMapper(stored), instanceMapper(), new ObjectMapper());

        EtlCheckpointSnapshot snapshot =
                store.loadOrCreate(7L, "orders", "a".repeat(64), "LONG", 0L, List.of(0L));
        EtlCheckpointSnapshot advanced = store.advanceAndGet(snapshot, 7L, 12L, List.of(99L));

        assertEquals(42L, snapshot.taskId());
        assertEquals(List.of(0), snapshot.tieBreakers());
        assertEquals("12", stored.get().getWatermarkValue());
        assertEquals("[99]", stored.get().getTieBreakerValue());
        assertEquals(1L, stored.get().getVersion());
        assertEquals(1L, advanced.version());
        assertEquals(12L, advanced.watermark());
    }

    @Test
    void decimalWatermarksSurvivePersistedRoundTrips() {
        AtomicReference<EtlCheckpoint> stored = new AtomicReference<>();
        var store =
                new EtlCheckpointStore(
                        checkpointMapper(stored), instanceMapper(), new ObjectMapper());
        var decimal = new java.math.BigDecimal("0.12345678901234567891");
        var snapshot =
                store.loadOrCreate(
                        7L, "decimal", "a".repeat(64), "DECIMAL", decimal, List.of(decimal));
        assertEquals(decimal, snapshot.watermark());
        assertEquals(decimal, snapshot.tieBreakers().getFirst());
    }

    private InstanceMapper instanceMapper() {
        Instance instance = new Instance();
        instance.setTargetType(TargetType.TASK);
        instance.setTargetId(42L);
        return (InstanceMapper)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {InstanceMapper.class},
                        (proxy, method, args) ->
                                method.getName().equals("selectById") ? instance : 0);
    }

    private EtlCheckpointMapper checkpointMapper(AtomicReference<EtlCheckpoint> stored) {
        return (EtlCheckpointMapper)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {EtlCheckpointMapper.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "insertIfAbsent" -> {
                                        if (stored.get() == null) {
                                            EtlCheckpoint value = (EtlCheckpoint) args[0];
                                            value.setVersion(0L);
                                            stored.set(value);
                                        }
                                        yield 1;
                                    }
                                    case "select" -> stored.get();
                                    case "advance" -> {
                                        EtlCheckpoint value = (EtlCheckpoint) args[0];
                                        long expected = (Long) args[1];
                                        if (stored.get().getVersion() != expected) {
                                            yield 0;
                                        }
                                        value.setVersion(expected + 1);
                                        stored.set(value);
                                        yield 1;
                                    }
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                });
    }
}
