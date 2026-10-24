// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.cluster.ClusterStateStore;
import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.core.trigger.TriggerCommandHandler;
import com.ifielsc.ijob.framework.runtime.RuntimeRunRegistry;
import com.ifielsc.ijob.infra.mapper.coordination.DatabaseClockMapper;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;

@Component
public class RuntimeTelemetryPublisher {
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(RuntimeTelemetryPublisher.class);
    private final RuntimeRunRegistry registry;
    private final TriggerCommandHandler worker;
    private final ClusterStateStore states;
    private final DatabaseClockMapper clock;
    private final AppProperties properties;
    private final ObjectMapper json;

    public RuntimeTelemetryPublisher(
            RuntimeRunRegistry registry,
            TriggerCommandHandler worker,
            ClusterStateStore states,
            DatabaseClockMapper clock,
            AppProperties properties,
            ObjectMapper json) {
        this.registry = registry;
        this.worker = worker;
        this.states = states;
        this.clock = clock;
        this.properties = properties;
        this.json = json;
    }

    @Scheduled(fixedDelayString = "${app.runtime.heartbeat-interval:5s}")
    public void publish() {
        String runId = registry.currentRunId();
        if (runId == null) return;
        try {
            var snapshot = new LinkedHashMap<String, Object>(worker.runtimeTelemetry());
            snapshot.put("runId", runId);
            snapshot.put("measuredAt", clock.currentTimestamp().toString());
            snapshot.put("scheduleEnabled", properties.getSchedule().isEnabled());
            states.set(
                    "ijob:runtime:" + runId,
                    json.writeValueAsString(snapshot),
                    Math.max(
                            1,
                            properties
                                    .getRuntime()
                                    .getMaintenance()
                                    .getStaleRunTimeout()
                                    .toSeconds()));
        } catch (Exception exception) {
            log.warn(
                    "Runtime telemetry publication failed, runId={}, failureType={}",
                    runId,
                    exception.getClass().getSimpleName());
        }
    }
}
