package com.ifelio.ijob.monitor.agent.collector.impl;

import com.ifelio.ijob.contract.monitor.CollectType;
import com.ifelio.ijob.contract.monitor.metric.MemoryMetric;
import com.ifelio.ijob.monitor.agent.collector.MetricCollector;
import com.ifelio.ijob.monitor.agent.collector.OshiCollectorSupport;
import com.ifelio.ijob.monitor.agent.model.MetricUtils;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import oshi.hardware.GlobalMemory;

@Component
@RequiredArgsConstructor
public class MemoryCollector implements MetricCollector<MemoryMetric> {

    private final OshiCollectorSupport support;

    @Override
    public CollectType type() {
        return CollectType.MEMORY;
    }

    @Override
    public MemoryMetric collect() {

        GlobalMemory memory = support.memory();

        long total = memory.getTotal();

        long available = memory.getAvailable();

        long used = total - available;

        double usage = percentage(total, available);

        return MemoryMetric.builder()
                .total(total)
                .available(available)
                .used(used)
                .free(available)
                .usage(usage)
                .build();
    }

    static double percentage(long total, long available) {
        if (total == 0) {
            return 0D;
        }
        return MetricUtils.percent((total - available) * 100D / total);
    }
}
