// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent.collector.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import oshi.software.os.OSProcess;

import java.lang.reflect.Proxy;
import java.util.Set;

class ProcessSnapshotCacheTest {

    @Test
    void retainOnlyShouldEvictUnobservedProcesses() {
        ProcessSnapshotCache cache = new ProcessSnapshotCache();
        cache.put(process(1, 100L));
        cache.put(process(2, 200L));

        cache.retainOnly(Set.of(2));

        assertNull(cache.get(1));
        assertEquals(1, cache.size());
    }

    @Test
    void pidReuseShouldNotBeTreatedAsSameProcess() {
        assertTrue(
                DefaultApplicationMetricCollector.isSameProcess(
                        process(7, 100L), process(7, 100L)));
        assertFalse(
                DefaultApplicationMetricCollector.isSameProcess(
                        process(7, 100L), process(7, 200L)));
    }

    private OSProcess process(int pid, long startTime) {
        return (OSProcess)
                Proxy.newProxyInstance(
                        OSProcess.class.getClassLoader(),
                        new Class<?>[] {OSProcess.class},
                        (proxy, method, arguments) ->
                                switch (method.getName()) {
                                    case "getProcessID" -> pid;
                                    case "getStartTime" -> startTime;
                                    default -> defaultValue(method.getReturnType());
                                });
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == long.class) {
            return 0L;
        }
        return 0;
    }
}
