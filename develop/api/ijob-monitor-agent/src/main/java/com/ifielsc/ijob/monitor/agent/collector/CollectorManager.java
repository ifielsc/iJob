// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent.collector;

import com.ifielsc.ijob.contract.monitor.CollectType;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CollectorManager {

    private final Map<CollectType, MetricCollector<?>> collectors;

    public CollectorManager(List<MetricCollector<?>> collectorList) {

        this.collectors =
                collectorList.stream()
                        .collect(Collectors.toMap(MetricCollector::type, Function.identity()));
    }

    @SuppressWarnings("unchecked")
    public <T> MetricCollector<T> getCollector(CollectType type) {

        return (MetricCollector<T>) collectors.get(type);
    }

    public boolean supports(CollectType type) {
        return collectors.containsKey(type);
    }

    public Set<CollectType> supportedTypes() {
        return collectors.keySet();
    }
}
