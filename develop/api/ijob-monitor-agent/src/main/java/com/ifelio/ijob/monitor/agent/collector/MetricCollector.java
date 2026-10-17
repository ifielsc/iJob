package com.ifelio.ijob.monitor.agent.collector;

import com.ifelio.ijob.contract.monitor.CollectType;

public interface MetricCollector<T> {

    CollectType type();

    T collect();
}
