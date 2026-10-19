package com.ifielsc.ijob.core.maintenance;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.observability.CoordinationObserver;
import com.ifielsc.ijob.infra.coordination.maintenance.CoordinationSnapshot;
import com.ifielsc.ijob.infra.mapper.coordination.CoordinationSnapshotMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class CoordinationSnapshotService {
    private final CoordinationSnapshotMapper mapper;
    private final AppProperties appProperties;
    private final CoordinationObserver observer;

    @Autowired
    public CoordinationSnapshotService(
            CoordinationSnapshotMapper mapper,
            AppProperties appProperties,
            Optional<CoordinationObserver> observer) {
        this.mapper = mapper;
        this.appProperties = appProperties;
        this.observer = observer.orElse(CoordinationObserver.NOOP);
    }

    public CoordinationSnapshot observe() {
        CoordinationSnapshot snapshot =
                mapper.selectSnapshot(
                        MaintenanceLeaderCoordinator.LEASE_NAME,
                        micros(appProperties.getRuntime().getMaintenance().getStaleRunTimeout()));
        record(snapshot);
        return snapshot;
    }

    private void record(CoordinationSnapshot snapshot) {
        observer.recordState("work", "ready", snapshot.getWorkReady());
        observer.recordState("work", "claimed", snapshot.getWorkClaimed());
        observer.recordState("work", "expired", snapshot.getWorkExpired());
        observer.recordState("work", "dead", snapshot.getWorkDead());
        observer.recordState("command", "pending", snapshot.getCommandPending());
        observer.recordState("command", "processing", snapshot.getCommandProcessing());
        observer.recordState("command", "expired", snapshot.getCommandExpired());
        observer.recordDelay("command", Duration.ofMillis(snapshot.getCommandOldestDelayMillis()));
        observer.recordState("outbox", "ready", snapshot.getOutboxReady());
        observer.recordState("outbox", "claimed", snapshot.getOutboxClaimed());
        observer.recordState("outbox", "expired", snapshot.getOutboxExpired());
        observer.recordState("outbox", "dead", snapshot.getOutboxDead());
        observer.recordState("permit", "active", snapshot.getPermitActive());
        observer.recordState("permit", "expired", snapshot.getPermitExpired());
        observer.recordState("app-run", "stale", snapshot.getStaleRuns());
        observer.recordState("app-run", "worker", snapshot.getActiveWorkerRuns());
        observer.recordState("app-run", "scheduler", snapshot.getActiveSchedulerRuns());
        observer.recordState("cluster-state", "total", snapshot.getClusterStateTotal());
        observer.recordState("cluster-state", "expired", snapshot.getClusterStateExpired());
        observer.recordDelay(
                "cluster-state", Duration.ofMillis(snapshot.getClusterStateOldestExpiredMillis()));
        observer.recordLease(
                MaintenanceLeaderCoordinator.LEASE_NAME,
                snapshot.getMaintenanceLeaderActive() > 0,
                0);
    }

    private long micros(Duration duration) {
        return Math.multiplyExact(duration.toMillis(), 1_000L);
    }
}
