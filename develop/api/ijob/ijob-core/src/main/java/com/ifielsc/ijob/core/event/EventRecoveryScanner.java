package com.ifielsc.ijob.core.event;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.RuntimeRole;
import com.ifielsc.ijob.common.observability.ScannerObserver;
import com.ifielsc.ijob.common.runtime.ApplicationRuntimeState;
import com.ifielsc.ijob.core.maintenance.MaintenanceLeaderCoordinator;
import com.ifielsc.ijob.core.trigger.TriggerCommand;
import com.ifielsc.ijob.core.trigger.TriggerCommandHandler;
import com.ifielsc.ijob.infra.entity.runtime.EventRecord;
import com.ifielsc.ijob.infra.entity.runtime.EventWait;
import com.ifielsc.ijob.infra.mapper.runtime.EventRecordMapper;
import com.ifielsc.ijob.infra.mapper.runtime.EventWaitMapper;
import com.ifielsc.ijob.infra.mapper.runtime.TriggerMapper;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
@ConditionalOnProperty(
        prefix = "app.event.recovery",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class EventRecoveryScanner {
    private final TriggerMapper triggerMapper;
    private final TriggerCommandHandler triggerCommandHandler;
    private final EventWaitMapper eventWaitMapper;
    private final EventRecordMapper eventRecordMapper;
    private final EventWaitResolutionService waitResolutionService;
    private final InstanceContinuationDispatcher continuationDispatcher;
    private final ApplicationRuntimeState runtimeState;
    private final AppProperties appProperties;
    private final ScannerObserver scannerObserver;
    private final int scanBatchSize;
    private final MaintenanceLeaderCoordinator maintenanceLeader;

    public EventRecoveryScanner(
            TriggerMapper triggerMapper,
            TriggerCommandHandler triggerCommandHandler,
            EventWaitMapper eventWaitMapper,
            EventRecordMapper eventRecordMapper,
            EventWaitResolutionService waitResolutionService,
            InstanceContinuationDispatcher continuationDispatcher,
            ApplicationRuntimeState runtimeState) {
        this(
                triggerMapper,
                triggerCommandHandler,
                eventWaitMapper,
                eventRecordMapper,
                waitResolutionService,
                continuationDispatcher,
                runtimeState,
                new AppProperties(),
                ScannerObserver.NOOP,
                100,
                null);
    }

    @Autowired
    public EventRecoveryScanner(
            TriggerMapper triggerMapper,
            TriggerCommandHandler triggerCommandHandler,
            EventWaitMapper eventWaitMapper,
            EventRecordMapper eventRecordMapper,
            EventWaitResolutionService waitResolutionService,
            InstanceContinuationDispatcher continuationDispatcher,
            ApplicationRuntimeState runtimeState,
            AppProperties appProperties,
            Optional<ScannerObserver> scannerObserver,
            MaintenanceLeaderCoordinator maintenanceLeader,
            @Value("${app.event.recovery.scan-batch-size:100}") int scanBatchSize) {
        this(
                triggerMapper,
                triggerCommandHandler,
                eventWaitMapper,
                eventRecordMapper,
                waitResolutionService,
                continuationDispatcher,
                runtimeState,
                appProperties,
                scannerObserver.orElse(ScannerObserver.NOOP),
                scanBatchSize,
                maintenanceLeader);
    }

    private EventRecoveryScanner(
            TriggerMapper triggerMapper,
            TriggerCommandHandler triggerCommandHandler,
            EventWaitMapper eventWaitMapper,
            EventRecordMapper eventRecordMapper,
            EventWaitResolutionService waitResolutionService,
            InstanceContinuationDispatcher continuationDispatcher,
            ApplicationRuntimeState runtimeState,
            AppProperties appProperties,
            ScannerObserver scannerObserver,
            int scanBatchSize,
            MaintenanceLeaderCoordinator maintenanceLeader) {
        this.triggerMapper = triggerMapper;
        this.triggerCommandHandler = triggerCommandHandler;
        this.eventWaitMapper = eventWaitMapper;
        this.eventRecordMapper = eventRecordMapper;
        this.waitResolutionService = waitResolutionService;
        this.continuationDispatcher = continuationDispatcher;
        this.runtimeState = runtimeState;
        this.appProperties = appProperties;
        this.scannerObserver = scannerObserver;
        this.scanBatchSize = scanBatchSize;
        this.maintenanceLeader = maintenanceLeader;
    }

    @Scheduled(fixedDelayString = "${app.event.recovery.scan-interval:1s}")
    public void scan() {
        if (!runtimeState.isReady()) {
            return;
        }
        if (appProperties.getRuntime().getMode().isPersistent()) {
            if (!appProperties.getRuntime().getRoles().contains(RuntimeRole.MAINTENANCE)
                    || maintenanceLeader == null) {
                return;
            }
            maintenanceLeader.runIfLeader(
                    () -> {
                        scanAsOwner();
                        return Boolean.TRUE;
                    });
            return;
        }
        if (!appProperties.getRuntime().getRoles().contains(RuntimeRole.WORKER)) {
            return;
        }
        scanAsOwner();
    }

    private void scanAsOwner() {
        long startedAt = System.nanoTime();
        boolean success = false;
        try {
            if (!appProperties.getRuntime().getMode().isPersistent()) {
                recoverDevelopmentTriggers();
            }
            recoverWaitingEvents();
            expireWaits();
            recoverContinuations();
            success = true;
        } finally {
            scannerObserver.recordRun(
                    "event-recovery", success, Duration.ofNanos(System.nanoTime() - startedAt));
        }
    }

    private void recoverDevelopmentTriggers() {
        List<Long> ids = triggerMapper.selectWaitingIds(scanBatchSize);
        scannerObserver.recordBatch("event-recovery", "waiting-trigger", ids.size(), scanBatchSize);
        for (Long id : ids) {
            try {
                triggerCommandHandler.handle(new TriggerCommand(id));
                scannerObserver.recordItem("event-recovery", "waiting-trigger", true);
            } catch (RuntimeException exception) {
                scannerObserver.recordItem("event-recovery", "waiting-trigger", false);
                log.error(
                        "event trigger recovery failed, triggerId={}",
                        id,
                        com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
            }
        }
    }

    private void recoverWaitingEvents() {
        List<EventWait> waits = eventWaitMapper.selectRecoverableWaitingBatch(scanBatchSize);
        scannerObserver.recordBatch("event-recovery", "waiting-event", waits.size(), scanBatchSize);
        for (EventWait wait : waits) {
            try {
                EventRecord event =
                        eventRecordMapper.selectFirstMatching(
                                wait.getEventCode(), wait.getBusinessKey(),
                                wait.getCorrelationKey(), wait.getAcceptAfter());
                if (event != null) {
                    waitResolutionService.match(event);
                }
                scannerObserver.recordItem("event-recovery", "waiting-event", true);
            } catch (RuntimeException exception) {
                scannerObserver.recordItem("event-recovery", "waiting-event", false);
                log.error(
                        "event wait recovery failed, waitId={}",
                        wait.getId(),
                        com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
            }
        }
    }

    private void expireWaits() {
        LocalDateTime now = LocalDateTime.now();
        List<EventWait> waits = eventWaitMapper.selectTimedOut(now, scanBatchSize);
        scannerObserver.recordBatch(
                "event-recovery", "timed-out-wait", waits.size(), scanBatchSize);
        for (EventWait wait : waits) {
            try {
                waitResolutionService.timeout(wait, now);
                scannerObserver.recordItem("event-recovery", "timed-out-wait", true);
            } catch (RuntimeException exception) {
                scannerObserver.recordItem("event-recovery", "timed-out-wait", false);
                log.error(
                        "event wait expiration failed, waitId={}",
                        wait.getId(),
                        com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
            }
        }
    }

    private void recoverContinuations() {
        List<Long> rootIds = eventWaitMapper.selectResolvedRootIds(scanBatchSize);
        scannerObserver.recordBatch(
                "event-recovery", "continuation", rootIds.size(), scanBatchSize);
        for (Long rootId : rootIds) {
            try {
                continuationDispatcher.dispatchAfterCommit(rootId);
                scannerObserver.recordItem("event-recovery", "continuation", true);
            } catch (RuntimeException exception) {
                scannerObserver.recordItem("event-recovery", "continuation", false);
                log.error(
                        "event continuation recovery failed, rootId={}",
                        rootId,
                        com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
            }
        }
    }
}
