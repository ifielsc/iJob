package com.ifielsc.ijob.infra.coordination.maintenance;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoordinationSnapshot {
    private long workReady;
    private long workClaimed;
    private long workExpired;
    private long workDead;
    private long commandPending;
    private long commandProcessing;
    private long commandExpired;
    private long commandOldestDelayMillis;
    private long outboxReady;
    private long outboxClaimed;
    private long outboxExpired;
    private long outboxDead;
    private long permitActive;
    private long permitExpired;
    private long staleRuns;
    private long maintenanceLeaderActive;
    private long activeWorkerRuns;
    private long activeSchedulerRuns;
    private long clusterStateTotal;
    private long clusterStateExpired;
    private long clusterStateOldestExpiredMillis;
}
