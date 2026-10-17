package com.ifelio.ijob;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.common.config.AppProperties.RuntimeRole;
import com.ifelio.ijob.common.observability.ScannerObserver;
import com.ifelio.ijob.common.runtime.ApplicationRuntimeState;
import com.ifelio.ijob.core.maintenance.CoordinationSnapshotService;
import com.ifelio.ijob.core.maintenance.MaintenanceLeaderCoordinator;
import com.ifelio.ijob.core.maintenance.RuntimeMaintenanceRecoveryService;
import com.ifelio.ijob.framework.runtime.RuntimeRunRegistry;
import com.ifelio.ijob.infra.coordination.command.DatabaseRuntimeCommandQueue;
import com.ifelio.ijob.infra.mapper.coordination.ClusterStateMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class RuntimeMaintenanceScanner {
    private static final Logger log = LoggerFactory.getLogger(RuntimeMaintenanceScanner.class);

    private final MaintenanceLeaderCoordinator leader;
    private final RuntimeMaintenanceRecoveryService recoveryService;
    private final CoordinationSnapshotService snapshotService;
    private final RuntimeRunRegistry runRegistry;
    private final ApplicationRuntimeState runtimeState;
    private final AppProperties.Maintenance properties;
    private final AppProperties appProperties;
    private final ScannerObserver scannerObserver;
    private final DatabaseRuntimeCommandQueue commandQueue;
    private final ClusterStateMapper clusterStateMapper;

    @Autowired
    public RuntimeMaintenanceScanner(
            MaintenanceLeaderCoordinator leader,
            RuntimeMaintenanceRecoveryService recoveryService,
            CoordinationSnapshotService snapshotService,
            RuntimeRunRegistry runRegistry,
            ApplicationRuntimeState runtimeState,
            AppProperties appProperties,
            DatabaseRuntimeCommandQueue commandQueue,
            ClusterStateMapper clusterStateMapper,
            Optional<ScannerObserver> scannerObserver) {
        this.leader = leader;
        this.recoveryService = recoveryService;
        this.snapshotService = snapshotService;
        this.runRegistry = runRegistry;
        this.runtimeState = runtimeState;
        this.properties = appProperties.getRuntime().getMaintenance();
        this.appProperties = appProperties;
        this.commandQueue = commandQueue;
        this.clusterStateMapper = clusterStateMapper;
        this.scannerObserver = scannerObserver.orElse(ScannerObserver.NOOP);
    }

    @Scheduled(fixedDelayString = "${app.runtime.maintenance.scan-interval:5s}")
    public void scan() {
        if (!appProperties.getRuntime().getMode().isPersistent()
                || !properties.isEnabled()
                || !runtimeState.isReady()
                || !appProperties.getRuntime().getRoles().contains(RuntimeRole.MAINTENANCE)
                || !leader.isLeader()) {
            return;
        }
        long startedAt = System.nanoTime();
        boolean success = false;
        try {
            int staleRuns = runBatches(this::markStaleRunsBatch);
            int satisfiedStops = runBatches(this::succeedCompletedStopsBatch);
            int expiredCommands = runBatches(this::expireCommandsBatch);
            int expiredClusterState = runBatches(this::deleteExpiredClusterStateBatch);
            int expiredQueuedWork = runBatches(this::expireQueuedWorkBatch);
            int timedOut = runBatches(this::recoverTimedOutBatch);
            int orphans = runBatches(this::recoverOrphanBatch);
            leader.runIfLeader(snapshotService::observe);
            log.info(
                    "runtime maintenance completed, staleRuns={}, satisfiedStops={},"
                            + " expiredCommands={}, expiredClusterState={}, expiredQueuedWork={},"
                            + " timedOutRoots={}, orphanRoots={}",
                    staleRuns,
                    satisfiedStops,
                    expiredCommands,
                    expiredClusterState,
                    expiredQueuedWork,
                    timedOut,
                    orphans);
            success = true;
        } finally {
            scannerObserver.recordRun(
                    "runtime-maintenance",
                    success,
                    Duration.ofNanos(System.nanoTime() - startedAt));
        }
    }

    private int runBatches(BatchAction action) {
        int total = 0;
        for (int batch = 0; batch < properties.getMaxBatchesPerRun(); batch++) {
            Optional<Integer> result = leader.runIfLeader(action::run);
            if (result.isEmpty()) {
                return total;
            }
            int count = result.get();
            total += count;
            if (count < properties.getBatchSize()) {
                break;
            }
        }
        return total;
    }

    private int markStaleRunsBatch() {
        int count =
                runRegistry.markStaleRunsCrashed(
                        properties.getStaleRunTimeout(), properties.getBatchSize());
        scannerObserver.recordBatch(
                "runtime-maintenance", "stale-run", count, properties.getBatchSize());
        return count;
    }

    private int recoverTimedOutBatch() {
        int count =
                recoveryService.recoverTimedOutBatch(
                        "maintenance-timeout", properties.getBatchSize());
        scannerObserver.recordBatch(
                "runtime-maintenance", "timed-out-root", count, properties.getBatchSize());
        return count;
    }

    private int expireQueuedWorkBatch() {
        int count =
                recoveryService.expireQueuedBatch(
                        "maintenance-queue-expiry", properties.getBatchSize());
        scannerObserver.recordBatch(
                "runtime-maintenance", "expired-queued-work", count, properties.getBatchSize());
        return count;
    }

    private int succeedCompletedStopsBatch() {
        int count = commandQueue.succeedCompletedStops(properties.getBatchSize());
        scannerObserver.recordBatch(
                "runtime-maintenance", "satisfied-stop", count, properties.getBatchSize());
        return count;
    }

    private int expireCommandsBatch() {
        int count = commandQueue.expirePending(properties.getBatchSize());
        scannerObserver.recordBatch(
                "runtime-maintenance", "expired-command", count, properties.getBatchSize());
        return count;
    }

    private int deleteExpiredClusterStateBatch() {
        int count = clusterStateMapper.deleteExpired(properties.getBatchSize());
        scannerObserver.recordBatch(
                "runtime-maintenance", "expired-cluster-state", count, properties.getBatchSize());
        return count;
    }

    private int recoverOrphanBatch() {
        int count =
                recoveryService.recoverDeadOrphanBatch(
                        "maintenance-orphan",
                        properties.getOrphanGrace(),
                        properties.getBatchSize());
        scannerObserver.recordBatch(
                "runtime-maintenance", "orphan-root", count, properties.getBatchSize());
        return count;
    }

    @FunctionalInterface
    private interface BatchAction {
        int run();
    }
}
