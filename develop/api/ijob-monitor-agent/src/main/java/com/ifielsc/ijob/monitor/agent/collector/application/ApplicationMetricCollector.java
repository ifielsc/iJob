// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent.collector.application;

import com.ifielsc.ijob.contract.monitor.application.ApplicationCollectConfig;
import com.ifielsc.ijob.contract.monitor.application.ApplicationMetric;

import java.util.List;

public interface ApplicationMetricCollector {

    List<ApplicationMetric> collect(List<ApplicationCollectConfig> applications);
}
