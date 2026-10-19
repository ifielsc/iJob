// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent.service;

import com.ifielsc.ijob.contract.monitor.AgentInfo;
import com.ifielsc.ijob.contract.monitor.CollectType;
import com.ifielsc.ijob.contract.monitor.MonitorCollectRequest;
import com.ifielsc.ijob.contract.monitor.MonitorReport;
import com.ifielsc.ijob.contract.monitor.application.ApplicationCollectConfig;
import com.ifielsc.ijob.contract.monitor.metric.CpuMetric;
import com.ifielsc.ijob.contract.monitor.metric.DiskMetric;
import com.ifielsc.ijob.contract.monitor.metric.MemoryMetric;
import com.ifielsc.ijob.contract.monitor.metric.NetworkMetric;
import com.ifielsc.ijob.monitor.agent.collector.CollectorManager;
import com.ifielsc.ijob.monitor.agent.collector.MetricCollector;
import com.ifielsc.ijob.monitor.agent.collector.application.ApplicationMetricCollector;
import com.ifielsc.ijob.monitor.agent.execution.MonitorCollectionExecutor;
import com.ifielsc.ijob.monitor.agent.model.AgentIdentity;
import com.ifielsc.ijob.monitor.agent.model.AgentRuntime;
import com.ifielsc.ijob.monitor.agent.validation.MonitorRequestValidator;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MonitorCollectService {

    private final CollectorManager collectorManager;

    private final ApplicationMetricCollector applicationCollector;

    private final AgentRuntime runtime;

    private final AgentIdentity identity;

    private final MonitorRequestValidator requestValidator;

    private final MonitorCollectionExecutor collectionExecutor;

    public boolean ping() {
        return true;
    }

    public AgentInfo getAgentInfo() {
        return identity.info(runtime);
    }

    public MonitorReport collect(MonitorCollectRequest request) {
        requestValidator.validate(request);
        return collectionExecutor.execute(() -> doCollect(request));
    }

    private MonitorReport doCollect(MonitorCollectRequest request) {
        long startedNanos = System.nanoTime();
        MonitorReport report = new MonitorReport();
        report.setSuccess(true);
        report.setAgent(getAgentInfo());
        for (CollectType type : request.getCollectItems()) {
            if (type == CollectType.APPLICATION) {
                report.setApplications(applicationCollector.collect(request.getApplications()));
                continue;
            }
            MetricCollector<?> collector = collectorManager.getCollector(type);
            if (collector == null) {
                throw new IllegalStateException("monitor collector is unavailable: " + type);
            }
            Object metric = collector.collect();
            writeMetric(report, type, metric, request.getApplications());
        }
        report.setElapsed(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
        report.setCollectTime(LocalDateTime.now(ZoneOffset.UTC));
        return report;
    }

    @SuppressWarnings("unchecked")
    private void writeMetric(
            MonitorReport report,
            CollectType type,
            Object metric,
            List<ApplicationCollectConfig> applications) {
        switch (type) {
            case CPU -> report.setCpu((CpuMetric) metric);
            case MEMORY -> report.setMemory((MemoryMetric) metric);
            case DISK -> report.setDisks((List<DiskMetric>) metric);
            case NETWORK -> report.setNetworks((List<NetworkMetric>) metric);
            case APPLICATION -> report.setApplications(applicationCollector.collect(applications));
            default -> throw new IllegalArgumentException("unsupported collect type");
        }
    }
}
