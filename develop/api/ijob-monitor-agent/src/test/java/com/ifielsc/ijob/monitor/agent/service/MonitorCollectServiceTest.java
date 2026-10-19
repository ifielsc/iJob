// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.contract.monitor.CollectType;
import com.ifielsc.ijob.contract.monitor.MonitorCollectRequest;
import com.ifielsc.ijob.contract.monitor.MonitorReport;
import com.ifielsc.ijob.monitor.agent.collector.CollectorManager;
import com.ifielsc.ijob.monitor.agent.collector.MetricCollector;
import com.ifielsc.ijob.monitor.agent.config.AgentProperties;
import com.ifielsc.ijob.monitor.agent.execution.MonitorCollectionExecutor;
import com.ifielsc.ijob.monitor.agent.model.AgentIdentity;
import com.ifielsc.ijob.monitor.agent.model.AgentRuntime;
import com.ifielsc.ijob.monitor.agent.validation.MonitorRequestValidator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

class MonitorCollectServiceTest {

    private MonitorCollectionExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    void shouldCollectApplicationsWithoutGenericMetricCollector() {
        AgentProperties properties = properties();
        executor = new MonitorCollectionExecutor(properties);
        var application =
                com.ifielsc.ijob.contract.monitor.application.ApplicationCollectConfig.builder()
                        .name("test-app")
                        .matchType(
                                com.ifielsc.ijob.contract.monitor.application.ProcessMatchType.PID)
                        .matchValue("123")
                        .build();
        var metric =
                com.ifielsc.ijob.contract.monitor.application.ApplicationMetric.builder()
                        .name("test-app")
                        .found(true)
                        .pid(123L)
                        .processCount(1)
                        .build();
        MonitorCollectService service =
                new MonitorCollectService(
                        new CollectorManager(List.of(cpuCollector())),
                        applications -> {
                            assertEquals(List.of(application), applications);
                            return List.of(metric);
                        },
                        new AgentRuntime(),
                        new AgentIdentity(properties),
                        new MonitorRequestValidator(),
                        executor);
        for (var items :
                List.of(
                        List.of(CollectType.APPLICATION),
                        List.of(CollectType.CPU, CollectType.APPLICATION))) {
            MonitorReport report =
                    service.collect(
                            MonitorCollectRequest.builder()
                                    .collectItems(items)
                                    .applications(List.of(application))
                                    .build());
            assertTrue(report.isSuccess());
            assertEquals(List.of(metric), report.getApplications());
            assertNotNull(report.getCollectTime());
        }
    }

    @Test
    void shouldPublishCompleteCollectionMetadata() {
        AgentProperties properties = new AgentProperties();
        properties.setHostname("worker-01");
        properties.setCollectTimeout(1_000);
        properties.setCollectConcurrency(1);
        properties.setCollectQueueCapacity(1);
        executor = new MonitorCollectionExecutor(properties);
        MonitorCollectService service =
                new MonitorCollectService(
                        new CollectorManager(List.of(cpuCollector())),
                        applications -> List.of(),
                        new AgentRuntime(),
                        new AgentIdentity(properties),
                        new MonitorRequestValidator(),
                        executor);

        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
        MonitorReport report =
                service.collect(
                        MonitorCollectRequest.builder()
                                .collectItems(List.of(CollectType.CPU))
                                .build());

        assertTrue(report.isSuccess());
        assertNotNull(report.getAgent());
        assertEquals("worker-01", report.getAgent().getHostname());
        assertNotNull(report.getAgent().getOsType());
        assertNotNull(report.getElapsed());
        assertTrue(report.getElapsed() >= 0);
        assertTrue(!report.getCollectTime().isBefore(before));
    }

    @Test
    void missingCollectorShouldFailInsteadOfReturningPartialSuccess() {
        AgentProperties properties = properties();
        executor = new MonitorCollectionExecutor(properties);
        MonitorCollectService service =
                new MonitorCollectService(
                        new CollectorManager(List.of()),
                        applications -> List.of(),
                        new AgentRuntime(),
                        new AgentIdentity(properties),
                        new MonitorRequestValidator(),
                        executor);

        assertThrows(
                IllegalStateException.class,
                () ->
                        service.collect(
                                MonitorCollectRequest.builder()
                                        .collectItems(List.of(CollectType.CPU))
                                        .build()));
    }

    private MetricCollector<Object> cpuCollector() {
        return new MetricCollector<>() {
            @Override
            public CollectType type() {
                return CollectType.CPU;
            }

            @Override
            public Object collect() {
                return com.ifielsc.ijob.contract.monitor.metric.CpuMetric.builder()
                        .usage(0D)
                        .build();
            }
        };
    }

    private AgentProperties properties() {
        AgentProperties properties = new AgentProperties();
        properties.setHostname("worker-01");
        properties.setCollectTimeout(1_000);
        properties.setCollectConcurrency(1);
        properties.setCollectQueueCapacity(1);
        return properties;
    }
}
