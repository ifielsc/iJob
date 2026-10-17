package com.ifelio.ijob.monitor.agent.collector.impl;

import com.ifelio.ijob.contract.monitor.CollectType;
import com.ifelio.ijob.contract.monitor.metric.CpuMetric;
import com.ifelio.ijob.monitor.agent.collector.MetricCollector;
import com.ifelio.ijob.monitor.agent.collector.OshiCollectorSupport;
import com.ifelio.ijob.monitor.agent.model.MetricUtils;

import org.springframework.stereotype.Component;

import oshi.hardware.CentralProcessor;

@Component
public class CpuCollector implements MetricCollector<CpuMetric> {

    private final OshiCollectorSupport support;

    /** Ticks from the preceding sample; the interval depends on collection calls. */
    private long[] previousTicks;

    public CpuCollector(OshiCollectorSupport support) {

        this.support = support;

        this.previousTicks = support.processor().getSystemCpuLoadTicks();
    }

    @Override
    public CollectType type() {
        return CollectType.CPU;
    }

    @Override
    public synchronized CpuMetric collect() {

        CentralProcessor processor = support.processor();

        double usage = percentage(processor.getSystemCpuLoadBetweenTicks(previousTicks));

        previousTicks = processor.getSystemCpuLoadTicks();

        double[] load = processor.getSystemLoadAverage(3);

        return CpuMetric.builder()
                .cores(processor.getLogicalProcessorCount())
                .usage(usage)
                .load1(load[0])
                .load5(load[1])
                .load15(load[2])
                .build();
    }

    static double percentage(double ratio) {
        return MetricUtils.percent(ratio * 100D);
    }
}
