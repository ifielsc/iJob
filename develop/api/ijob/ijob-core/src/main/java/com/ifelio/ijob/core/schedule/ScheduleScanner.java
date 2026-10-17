package com.ifelio.ijob.core.schedule;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.common.config.AppProperties.RuntimeRole;
import com.ifelio.ijob.common.observability.ScannerObserver;
import com.ifelio.ijob.common.runtime.ApplicationRuntimeState;
import com.ifelio.ijob.core.trigger.TriggerService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "app.schedule", name = "enabled", havingValue = "true")
public class ScheduleScanner {
    private final ScheduleService scheduleService;
    private final TriggerService triggerService;
    private final ApplicationRuntimeState runtimeState;
    private final AppProperties appProperties;
    private final ScannerObserver scannerObserver;
    private final int scanBatchSize;

    public ScheduleScanner(
            ScheduleService scheduleService,
            TriggerService triggerService,
            ApplicationRuntimeState runtimeState) {
        this(
                scheduleService,
                triggerService,
                runtimeState,
                new AppProperties(),
                ScannerObserver.NOOP,
                100);
    }

    @Autowired
    public ScheduleScanner(
            ScheduleService scheduleService,
            TriggerService triggerService,
            ApplicationRuntimeState runtimeState,
            AppProperties appProperties,
            Optional<ScannerObserver> scannerObserver,
            @Value("${app.schedule.scan-batch-size:100}") int scanBatchSize) {
        this(
                scheduleService,
                triggerService,
                runtimeState,
                appProperties,
                scannerObserver.orElse(ScannerObserver.NOOP),
                scanBatchSize);
    }

    private ScheduleScanner(
            ScheduleService scheduleService,
            TriggerService triggerService,
            ApplicationRuntimeState runtimeState,
            AppProperties appProperties,
            ScannerObserver scannerObserver,
            int scanBatchSize) {
        this.scheduleService = scheduleService;
        this.triggerService = triggerService;
        this.runtimeState = runtimeState;
        this.appProperties = appProperties;
        this.scannerObserver = scannerObserver;
        this.scanBatchSize = scanBatchSize;
    }

    @Scheduled(
            initialDelayString = "${app.schedule.initial-delay:5s}",
            fixedDelayString = "${app.schedule.scan-interval:1s}")
    public void scan() {
        if (!runtimeState.isReady()
                || !appProperties.getRuntime().getRoles().contains(RuntimeRole.SCHEDULER)) {
            return;
        }
        long startedAt = System.nanoTime();
        boolean success = false;
        try {
            List<Long> scheduleIds = scheduleService.listDueScheduleIds(scanBatchSize);
            scannerObserver.recordBatch("schedule", "due", scheduleIds.size(), scanBatchSize);
            int remaining = appProperties.getSchedule().getMaxTriggersPerScan();
            for (Long scheduleId : scheduleIds) {
                if (remaining <= 0) {
                    break;
                }
                try {
                    remaining -= triggerService.trigger(scheduleId, remaining);
                    scannerObserver.recordItem("schedule", "due", true);
                } catch (Exception exception) {
                    scannerObserver.recordItem("schedule", "due", false);
                    log.error(
                            "schedule trigger failed, scheduleId={}",
                            scheduleId,
                            com.ifelio.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
                }
            }
            success = true;
        } finally {
            scannerObserver.recordRun(
                    "schedule", success, Duration.ofNanos(System.nanoTime() - startedAt));
        }
    }
}
