// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.etl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.infra.entity.runtime.EtlCheckpoint;
import com.ifielsc.ijob.infra.entity.runtime.Instance;
import com.ifielsc.ijob.infra.mapper.runtime.EtlCheckpointMapper;
import com.ifielsc.ijob.infra.mapper.runtime.InstanceMapper;

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

    @Test
    void jdbcTimestampWatermarksKeepLocalTimeAndNanosecondsAcrossReloads() {
        AtomicReference<EtlCheckpoint> stored = new AtomicReference<>();
        var mapper = new ObjectMapper();
        mapper.disable(
                com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setTimeZone(java.util.TimeZone.getTimeZone("Pacific/Honolulu"));
        var store = new EtlCheckpointStore(checkpointMapper(stored), instanceMapper(), mapper);
        var initial = java.sql.Timestamp.valueOf("2026-01-01 12:34:56.123456789");
        var next = java.sql.Timestamp.valueOf("2026-01-02 12:34:56.987654321");
        var snapshot =
                store.loadOrCreate(7L, "time", "a".repeat(64), "TIMESTAMP", initial, List.of(0));
        assertEquals("2026-01-01T12:34:56.123456789", snapshot.watermark());
        store.advance(snapshot, 7L, next, List.of(3));
        var resumed =
                store.loadOrCreate(7L, "time", "a".repeat(64), "TIMESTAMP", initial, List.of(0));
        assertEquals("2026-01-02T12:34:56.987654321", resumed.watermark());
        assertEquals(List.of(3), resumed.tieBreakers());
    }

    @Test
    void jdbcDateWatermarksKeepTheirCalendarDate() {
        AtomicReference<EtlCheckpoint> stored = new AtomicReference<>();
        var store =
                new EtlCheckpointStore(
                        checkpointMapper(stored), instanceMapper(), new ObjectMapper());
        var snapshot =
                store.loadOrCreate(
                        7L,
                        "date",
                        "a".repeat(64),
                        "DATE",
                        java.sql.Date.valueOf("2026-01-02"),
                        List.of());
        assertEquals("2026-01-02", snapshot.watermark());
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
