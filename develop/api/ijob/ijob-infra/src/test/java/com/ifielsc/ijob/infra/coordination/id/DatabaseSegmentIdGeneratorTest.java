// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.coordination.id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

class DatabaseSegmentIdGeneratorTest {
    @Test
    void nodesShouldAllocateDisjointRangesWithinEachNamespace() throws Exception {
        var watermarks = new ConcurrentHashMap<String, AtomicLong>();
        IdSegmentProvider provider =
                (key, size) -> {
                    long start =
                            watermarks
                                    .computeIfAbsent(key, ignored -> new AtomicLong(1))
                                    .getAndAdd(size);
                    return new IdSegmentRange(start, start + size);
                };
        var first = new DatabaseSegmentIdGenerator(provider, 1000);
        var second = new DatabaseSegmentIdGenerator(provider, 1000);
        Set<Long> tasks = ConcurrentHashMap.newKeySet();
        Set<Long> logs = ConcurrentHashMap.newKeySet();
        var futures = new ArrayList<Future<?>>();
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int worker = 0; worker < 8; worker++) {
                var generator = worker % 2 == 0 ? first : second;
                futures.add(
                        executor.submit(
                                () -> {
                                    for (int i = 0; i < 500; i++) {
                                        if (!tasks.add(generator.nextId(IdNamespace.TASK)))
                                            throw new AssertionError("Duplicate task");
                                        if (!logs.add(generator.nextId(IdNamespace.INSTANCE_LOG)))
                                            throw new AssertionError("Duplicate log");
                                    }
                                }));
            }
            for (Future<?> future : futures) future.get();
        }
        assertEquals(4000, tasks.size());
        assertEquals(4000, logs.size());
        assertEquals(4001L, watermarks.get(IdNamespace.TASK.segmentKey()).get());
        assertEquals(4001L, watermarks.get(IdNamespace.INSTANCE_LOG.segmentKey()).get());
    }

    @Test
    void managementNumbersShouldIgnoreRuntimeTrafficAndRestart() {
        var watermarks = new ConcurrentHashMap<String, AtomicLong>();
        IdSegmentProvider provider =
                (key, size) -> {
                    long start =
                            watermarks
                                    .computeIfAbsent(key, ignored -> new AtomicLong(1))
                                    .getAndAdd(size);
                    return new IdSegmentRange(start, start + size);
                };
        var first = new DatabaseSegmentIdGenerator(provider, 1000);
        assertEquals(1, first.nextId(IdNamespace.CALENDAR));
        assertEquals(1, first.nextId(IdNamespace.TASK));
        assertEquals(1, first.nextId(IdNamespace.JOB));
        for (int i = 0; i < 1234; i++) first.nextId(IdNamespace.INSTANCE_LOG);
        var restarted = new DatabaseSegmentIdGenerator(provider, 1000);
        assertEquals(2, restarted.nextId(IdNamespace.CALENDAR));
        assertEquals(2, restarted.nextId(IdNamespace.TASK));
        assertEquals(2001, restarted.nextId(IdNamespace.INSTANCE_LOG));
        assertEquals(1, restarted.nextId(IdNamespace.JOB_DEFINITION_NODE));
        assertEquals(101, watermarks.get(IdNamespace.JOB_DEFINITION_NODE.segmentKey()).get());
    }

    @Test
    void rangeShouldRejectInvalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> new IdSegmentRange(0, 10));
    }
}
