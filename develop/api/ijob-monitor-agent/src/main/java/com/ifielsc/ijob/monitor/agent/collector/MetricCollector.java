package com.ifielsc.ijob.monitor.agent.collector;

import com.ifielsc.ijob.contract.monitor.CollectType;

public interface MetricCollector<T> {

    CollectType type();

    T collect();
}
