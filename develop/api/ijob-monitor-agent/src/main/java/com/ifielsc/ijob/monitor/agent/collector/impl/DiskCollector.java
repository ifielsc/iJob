// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent.collector.impl;

import com.ifielsc.ijob.contract.monitor.CollectType;
import com.ifielsc.ijob.contract.monitor.metric.DiskMetric;
import com.ifielsc.ijob.monitor.agent.collector.MetricCollector;
import com.ifielsc.ijob.monitor.agent.collector.OshiCollectorSupport;
import com.ifielsc.ijob.monitor.agent.model.MetricUtils;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import oshi.software.os.OSFileStore;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DiskCollector implements MetricCollector<List<DiskMetric>> {

    private final OshiCollectorSupport support;

    @Override
    public CollectType type() {
        return CollectType.DISK;
    }

    @Override
    public List<DiskMetric> collect() {

        List<DiskMetric> result = new ArrayList<>();

        for (OSFileStore store : support.fileSystem().getFileStores()) {

            long total = store.getTotalSpace();

            long free = store.getUsableSpace();

            long used = total - free;

            double usage = total == 0 ? 0 : used * 100D / total;

            result.add(
                    DiskMetric.builder()
                            .path(store.getMount())
                            .fileSystem(store.getName())
                            .label(store.getLabel())
                            .type(store.getType())
                            .total(total)
                            .used(used)
                            .free(free)
                            .usage(MetricUtils.percent(usage))
                            .build());
        }

        return result;
    }
}
