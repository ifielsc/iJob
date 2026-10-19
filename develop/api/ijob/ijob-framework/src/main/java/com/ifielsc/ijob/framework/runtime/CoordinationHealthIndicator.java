// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.framework.runtime;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.runtime.ApplicationRuntimeState;
import com.ifielsc.ijob.infra.coordination.maintenance.CoordinationSnapshot;
import com.ifielsc.ijob.infra.mapper.coordination.CoordinationSnapshotMapper;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component("coordination")
public class CoordinationHealthIndicator implements HealthIndicator {
    private static final String MAINTENANCE_LEASE = "runtime-maintenance";

    private final CoordinationSnapshotMapper mapper;
    private final AppProperties appProperties;
    private final ApplicationRuntimeState runtimeState;

    public CoordinationHealthIndicator(
            CoordinationSnapshotMapper mapper,
            AppProperties appProperties,
            ApplicationRuntimeState runtimeState) {
        this.mapper = mapper;
        this.appProperties = appProperties;
        this.runtimeState = runtimeState;
    }

    @Override
    public Health health() {
        if (!appProperties.getRuntime().getMode().isPersistent()) {
            return Health.up().withDetail("persistentCoordination", false).build();
        }
        try {
            CoordinationSnapshot snapshot =
                    mapper.selectSnapshot(
                            MAINTENANCE_LEASE,
                            micros(
                                    appProperties
                                            .getRuntime()
                                            .getMaintenance()
                                            .getStaleRunTimeout()));
            boolean leaderRequired =
                    runtimeState.isReady()
                            && appProperties.getRuntime().getMaintenance().isEnabled();
            boolean leaderActive = snapshot.getMaintenanceLeaderActive() > 0;
            boolean workerRequired = snapshot.getWorkReady() > 0 || snapshot.getOutboxReady() > 0;
            boolean workerActive = snapshot.getActiveWorkerRuns() > 0;
            boolean schedulerRequired =
                    runtimeState.isReady() && appProperties.getSchedule().isEnabled();
            boolean schedulerActive = snapshot.getActiveSchedulerRuns() > 0;
            Health.Builder builder =
                    leaderRequired && !leaderActive
                                    || workerRequired && !workerActive
                                    || schedulerRequired && !schedulerActive
                            ? Health.down()
                            : Health.up();
            boolean attentionRequired =
                    snapshot.getWorkReady() > 0
                            || snapshot.getWorkExpired() > 0
                            || snapshot.getWorkDead() > 0
                            || snapshot.getCommandExpired() > 0
                            || snapshot.getOutboxReady() > 0
                            || snapshot.getOutboxExpired() > 0
                            || snapshot.getOutboxDead() > 0
                            || snapshot.getPermitExpired() > 0
                            || snapshot.getStaleRuns() > 0
                            || snapshot.getClusterStateExpired() > 0
                            || workerRequired && !workerActive
                            || schedulerRequired && !schedulerActive;
            return builder.withDetail("maintenanceLeaderActive", leaderActive)
                    .withDetail("workerActive", workerActive)
                    .withDetail("schedulerActive", schedulerActive)
                    .withDetail("attentionRequired", attentionRequired)
                    .withDetail("workReady", snapshot.getWorkReady())
                    .withDetail("workExpired", snapshot.getWorkExpired())
                    .withDetail("workDead", snapshot.getWorkDead())
                    .withDetail("commandPending", snapshot.getCommandPending())
                    .withDetail("commandExpired", snapshot.getCommandExpired())
                    .withDetail("commandOldestDelayMillis", snapshot.getCommandOldestDelayMillis())
                    .withDetail("outboxReady", snapshot.getOutboxReady())
                    .withDetail("outboxExpired", snapshot.getOutboxExpired())
                    .withDetail("outboxDead", snapshot.getOutboxDead())
                    .withDetail("permitExpired", snapshot.getPermitExpired())
                    .withDetail("staleRuns", snapshot.getStaleRuns())
                    .withDetail("clusterStateTotal", snapshot.getClusterStateTotal())
                    .withDetail("clusterStateExpired", snapshot.getClusterStateExpired())
                    .withDetail(
                            "clusterStateOldestExpiredMillis",
                            snapshot.getClusterStateOldestExpiredMillis())
                    .build();
        } catch (RuntimeException exception) {
            return Health.down()
                    .withDetail("persistentCoordination", true)
                    .withDetail("failureType", exception.getClass().getSimpleName())
                    .build();
        }
    }

    private long micros(Duration duration) {
        return Math.multiplyExact(duration.toMillis(), 1_000L);
    }
}
