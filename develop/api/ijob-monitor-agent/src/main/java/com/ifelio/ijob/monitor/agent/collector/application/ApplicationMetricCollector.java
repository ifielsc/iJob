package com.ifelio.ijob.monitor.agent.collector.application;

import com.ifelio.ijob.contract.monitor.application.ApplicationCollectConfig;
import com.ifelio.ijob.contract.monitor.application.ApplicationMetric;

import java.util.List;

public interface ApplicationMetricCollector {

    List<ApplicationMetric> collect(List<ApplicationCollectConfig> applications);
}
