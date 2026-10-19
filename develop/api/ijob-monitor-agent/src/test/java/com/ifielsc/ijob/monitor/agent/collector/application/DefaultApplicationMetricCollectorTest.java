// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent.collector.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.ifielsc.ijob.contract.monitor.application.ApplicationCollectConfig;
import com.ifielsc.ijob.contract.monitor.application.ApplicationMetric;
import com.ifielsc.ijob.contract.monitor.application.ProcessMatchType;
import com.ifielsc.ijob.monitor.agent.collector.OshiCollectorSupport;
import com.ifielsc.ijob.monitor.agent.collector.application.matcher.ProcessMatcherManager;
import com.ifielsc.ijob.monitor.agent.collector.application.matcher.impl.ProcessNameMatcher;

import org.junit.jupiter.api.Test;

import oshi.software.os.OSProcess;

import java.lang.reflect.Proxy;
import java.util.List;

class DefaultApplicationMetricCollectorTest {

    @Test
    void sameProcessShouldUsePreviousTickForEveryMatchingApplication() {
        OSProcess previous = process(null, 7, 100L, "java", 0D);
        OSProcess current = process(previous, 7, 100L, "java", 0.5D);
        ProcessSnapshotCache cache = new ProcessSnapshotCache();
        cache.put(previous);
        DefaultApplicationMetricCollector collector = collector(List.of(current), cache);

        List<ApplicationMetric> metrics =
                collector.collect(List.of(application("first"), application("second")));

        assertEquals(50D, metrics.get(0).getCpuUsage());
        assertEquals(50D, metrics.get(1).getCpuUsage());
    }

    @Test
    void unmatchedRunningProcessShouldRemainAvailableForNextRequest() {
        OSProcess previous = process(null, 8, 200L, "worker", 0D);
        OSProcess current = process(previous, 8, 200L, "worker", 0.25D);
        ProcessSnapshotCache cache = new ProcessSnapshotCache();
        cache.put(previous);
        DefaultApplicationMetricCollector collector = collector(List.of(current), cache);

        collector.collect(List.of(application("other", "other")));

        assertSame(current, cache.get(8));
    }

    private DefaultApplicationMetricCollector collector(
            List<OSProcess> processes, ProcessSnapshotCache cache) {
        OshiCollectorSupport support =
                new OshiCollectorSupport(null, null) {
                    @Override
                    public List<OSProcess> processes() {
                        return processes;
                    }
                };
        return new DefaultApplicationMetricCollector(
                support, new ProcessMatcherManager(List.of(new ProcessNameMatcher())), cache);
    }

    private ApplicationCollectConfig application(String name) {
        return application(name, "java");
    }

    private ApplicationCollectConfig application(String name, String processName) {
        return ApplicationCollectConfig.builder()
                .name(name)
                .matchType(ProcessMatchType.PROCESS_NAME)
                .matchValue(processName)
                .required(true)
                .allowMultiple(false)
                .build();
    }

    private OSProcess process(
            OSProcess expectedPrevious, int pid, long startTime, String name, double load) {
        return (OSProcess)
                Proxy.newProxyInstance(
                        OSProcess.class.getClassLoader(),
                        new Class<?>[] {OSProcess.class},
                        (proxy, method, arguments) ->
                                switch (method.getName()) {
                                    case "getProcessID" -> pid;
                                    case "getStartTime" -> startTime;
                                    case "getName" -> name;
                                    case "getResidentSetSize" -> 100L;
                                    case "getCommandLine" -> name;
                                    case "getProcessCpuLoadBetweenTicks" ->
                                            arguments != null
                                                            && arguments.length == 1
                                                            && arguments[0] == expectedPrevious
                                                    ? load
                                                    : 0D;
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
