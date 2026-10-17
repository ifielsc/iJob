package com.ifelio.ijob.core.retention;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.common.config.AppProperties.RuntimeRole;
import com.ifelio.ijob.common.observability.ScannerObserver;
import com.ifelio.ijob.common.runtime.ApplicationRuntimeState;
import com.ifelio.ijob.core.maintenance.MaintenanceLeaderCoordinator;
import com.ifelio.ijob.core.retention.RuntimeDataRetentionService.RetentionCutoffs;
import com.ifelio.ijob.core.retention.RuntimeDataRetentionService.RetentionSummary;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "app.retention", name = "enabled", havingValue = "true")
public class RuntimeDataRetentionScanner {
    private final RuntimeDataRetentionService retentionService;
    private final ApplicationRuntimeState runtimeState;
    private final AppProperties.Retention properties;
    private final ScannerObserver scannerObserver;
    private final MaintenanceLeaderCoordinator maintenanceLeader;
    private final AppProperties appProperties;

    @Autowired
    public RuntimeDataRetentionScanner(
            RuntimeDataRetentionService retentionService,
            ApplicationRuntimeState runtimeState,
            AppProperties appProperties,
            Optional<ScannerObserver> scannerObserver,
            MaintenanceLeaderCoordinator maintenanceLeader) {
        this.retentionService = retentionService;
        this.runtimeState = runtimeState;
        this.properties = appProperties.getRetention();
        this.scannerObserver = scannerObserver.orElse(ScannerObserver.NOOP);
        this.maintenanceLeader = maintenanceLeader;
        this.appProperties = appProperties;
    }

    @Scheduled(
            initialDelayString = "${app.retention.initial-delay:1m}",
            fixedDelayString = "${app.retention.interval:1h}")
    public void scan() {
        if (!runtimeState.isReady()
                || !appProperties.getRuntime().getRoles().contains(RuntimeRole.MAINTENANCE)) {
            return;
        }
        maintenanceLeader.runIfLeader(
                () -> {
                    scanAsLeader();
                    return Boolean.TRUE;
                });
    }

    private void scanAsLeader() {
        long startedAt = System.nanoTime();
        boolean success = false;
        try {
            RetentionCutoffs cutoffs = cutoffs(LocalDateTime.now());
            if (properties.isDryRun()) {
                RetentionSummary candidates = retentionService.preview(cutoffs);
                observe(candidates);
                log.info(
                        "runtime data retention dry-run completed, "
                                + "executionCandidates={}, notificationCandidates={}, "
                                + "eventCandidates={}, appRunCandidates={}",
                        candidates.executions(),
                        candidates.notifications(),
                        candidates.events(),
                        candidates.appRuns());
                success = true;
                return;
            }

            RetentionSummary total = RetentionSummary.empty();
            for (int batch = 0; batch < properties.getMaxBatchesPerRun(); batch++) {
                RetentionSummary deleted =
                        retentionService.cleanupBatch(cutoffs, properties.getBatchSize());
                total = total.plus(deleted);
                if (!deleted.mayHaveMore(properties.getBatchSize())) {
                    break;
                }
            }
            observe(total);
            log.info(
                    "runtime data retention cleanup completed, "
                            + "deletedExecutions={}, deletedNotifications={}, "
                            + "deletedEvents={}, deletedAppRuns={}",
                    total.executions(),
                    total.notifications(),
                    total.events(),
                    total.appRuns());
            success = true;
        } finally {
            scannerObserver.recordRun(
                    "retention", success, Duration.ofNanos(System.nanoTime() - startedAt));
        }
    }

    private RetentionCutoffs cutoffs(LocalDateTime now) {
        return new RetentionCutoffs(
                now.minus(properties.getExecution()),
                now.minus(properties.getNotification()),
                now.minus(properties.getEvent()),
                now.minus(properties.getAppRun()));
    }

    private void observe(RetentionSummary summary) {
        int limit = properties.getBatchSize();
        scannerObserver.recordBatch("retention", "execution", bounded(summary.executions()), limit);
        scannerObserver.recordBatch(
                "retention", "notification", bounded(summary.notifications()), limit);
        scannerObserver.recordBatch("retention", "event", bounded(summary.events()), limit);
        scannerObserver.recordBatch("retention", "app-run", bounded(summary.appRuns()), limit);
    }

    private int bounded(long value) {
        return (int) Math.min(Integer.MAX_VALUE, value);
    }
}
