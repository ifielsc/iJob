// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.trigger;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.RuntimeRole;
import com.ifielsc.ijob.common.diagnostic.DiagnosticContext;
import com.ifielsc.ijob.common.diagnostic.SafeDiagnostic;
import com.ifielsc.ijob.common.observability.CoordinationObserver;
import com.ifielsc.ijob.common.runtime.ApplicationRuntimeState;
import com.ifielsc.ijob.common.runtime.RuntimeFenceListener;
import com.ifielsc.ijob.contract.enums.TriggerStatus;
import com.ifielsc.ijob.core.error.CoreMessage;
import com.ifielsc.ijob.core.execute.ExecutionManager;
import com.ifielsc.ijob.core.execute.RuntimeCommandProcessor;
import com.ifielsc.ijob.infra.coordination.work.DatabaseTriggerWorkQueue;
import com.ifielsc.ijob.infra.coordination.work.ExecutionFenceCoordinator;
import com.ifielsc.ijob.infra.coordination.work.TriggerWorkClaim;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class TriggerCommandHandler implements RuntimeFenceListener {
    private final Executor executor;
    private final TriggerExecuteService triggerExecuteService;
    private final TriggerRuntimeService triggerRuntimeService;
    private final DatabaseTriggerWorkQueue workQueue;
    private final AppProperties appProperties;
    private final ApplicationRuntimeState runtimeState;
    private final ExecutionFenceCoordinator fenceCoordinator;
    private final RuntimeCommandProcessor runtimeCommandProcessor;
    private final ExecutionManager executionManager;
    private final CoordinationObserver coordinationObserver;
    private final String leaseOwner;
    private final Set<Long> dispatchedTriggerIds = ConcurrentHashMap.newKeySet();
    private final Map<Long, TriggerWorkClaim> activeClaims = new ConcurrentHashMap<>();
    private final AtomicInteger inflightClaimSlots = new AtomicInteger();
    private volatile long nextWorkScanNanos;
    private volatile int consecutiveEmptyScans;
    private volatile java.time.LocalDateTime lastScanAt;
    private volatile java.time.LocalDateTime lastRenewAt;
    private volatile long lastScanNanos;
    private volatile long lastRenewNanos;
    private final java.util.concurrent.atomic.AtomicLong renewalFailures =
            new java.util.concurrent.atomic.AtomicLong();
    private volatile long lastRenewalFailureNanos;

    public Map<String, Object> runtimeTelemetry() {
        long now = System.nanoTime();
        var queue = appProperties.getRuntime().getWorkQueue();
        long scanLimit =
                queue.getMaxIdleScanInterval()
                        .plus(queue.getScanInterval())
                        .plusSeconds(10)
                        .toNanos();
        long renewLimit = queue.getRenewInterval().multipliedBy(3).plusSeconds(5).toNanos();
        boolean healthy =
                runtimeState.isReady()
                        && lastScanNanos != 0
                        && now - lastScanNanos < scanLimit
                        && lastRenewNanos != 0
                        && now - lastRenewNanos < renewLimit
                        && (lastRenewalFailureNanos == 0
                                || now - lastRenewalFailureNanos > renewLimit);
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("healthy", healthy);
        values.put("active", activeClaims.size());
        values.put("capacity", queue.getMaxInflightRoots());
        values.put("renewalFailures", renewalFailures.get());
        values.put("lastScanAt", lastScanAt == null ? null : lastScanAt.toString());
        values.put("lastRenewAt", lastRenewAt == null ? null : lastRenewAt.toString());
        return values;
    }

    @Autowired
    public TriggerCommandHandler(
            @Qualifier("triggerExecutor") Executor executor,
            @Lazy TriggerExecuteService triggerExecuteService,
            TriggerRuntimeService triggerRuntimeService,
            DatabaseTriggerWorkQueue workQueue,
            AppProperties appProperties,
            ApplicationRuntimeState runtimeState,
            ExecutionFenceCoordinator fenceCoordinator,
            ExecutionManager executionManager,
            @Lazy RuntimeCommandProcessor runtimeCommandProcessor,
            Optional<CoordinationObserver> coordinationObserver) {
        this.executor = executor;
        this.triggerExecuteService = triggerExecuteService;
        this.triggerRuntimeService = triggerRuntimeService;
        this.workQueue = workQueue;
        this.appProperties = appProperties;
        this.runtimeState = runtimeState;
        this.fenceCoordinator = fenceCoordinator;
        this.executionManager = executionManager;
        this.runtimeCommandProcessor = runtimeCommandProcessor;
        this.coordinationObserver = coordinationObserver.orElse(CoordinationObserver.NOOP);
        this.leaseOwner = resolveLeaseOwner(appProperties);
    }

    public TriggerCommandHandler(
            Executor executor,
            TriggerExecuteService triggerExecuteService,
            TriggerRuntimeService triggerRuntimeService) {
        this.executor = executor;
        this.triggerExecuteService = triggerExecuteService;
        this.triggerRuntimeService = triggerRuntimeService;
        this.workQueue = null;
        this.appProperties = null;
        this.runtimeState = null;
        this.fenceCoordinator = null;
        this.executionManager = null;
        this.runtimeCommandProcessor = null;
        this.coordinationObserver = CoordinationObserver.NOOP;
        this.leaseOwner = null;
    }

    public void handle(TriggerCommand command) {
        Long triggerId = command.getTriggerId();
        if (persistentQueueEnabled()
                && (!workerRoleEnabled() || runtimeState == null || !runtimeState.isReady())) {
            return;
        }
        boolean claimSlotReserved = persistentQueueEnabled() && reserveClaimSlot();
        if (persistentQueueEnabled() && !claimSlotReserved) {
            return;
        }
        if (!dispatchedTriggerIds.add(triggerId)) {
            releaseClaimSlot(claimSlotReserved);
            log.debug("trigger is already dispatched, triggerId={}", triggerId);
            return;
        }
        TriggerWorkClaim claim = null;
        if (persistentQueueEnabled()) {
            try {
                claim = workQueue.claim(triggerId, leaseOwner);
            } catch (RuntimeException | Error failure) {
                clearLocalClaim(triggerId, null, claimSlotReserved);
                throw failure;
            }
            if (claim == null) {
                dispatchedTriggerIds.remove(triggerId);
                releaseClaimSlot(claimSlotReserved);
                return;
            }
            try {
                fenceCoordinator.activate(claim);
                activeClaims.put(triggerId, claim);
                executionManager.clearTriggerCancellation(triggerId);
                if (claim.attemptCount() > 1) {
                    coordinationObserver.recordEvent("work", "takeover");
                }
            } catch (RuntimeException | Error exception) {
                try {
                    workQueue.deferUnstarted(claim, "Local execution claim registration failed");
                } catch (RuntimeException retryFailure) {
                    exception.addSuppressed(retryFailure);
                } finally {
                    clearLocalClaim(triggerId, claim, claimSlotReserved);
                }
                throw exception;
            }
        }
        TriggerWorkClaim acceptedClaim = claim;
        try {
            executor.execute(
                    () -> {
                        TriggerExecutionDisposition disposition = null;
                        java.util.Map<String, String> correlation = new java.util.HashMap<>();
                        correlation.put("traceId", "trigger-" + triggerId);
                        correlation.put("triggerId", String.valueOf(triggerId));
                        if (acceptedClaim != null) {
                            correlation.put(
                                    "attempt", String.valueOf(acceptedClaim.attemptCount()));
                            correlation.put("fence", String.valueOf(acceptedClaim.fenceToken()));
                        }
                        try (var ignored = new DiagnosticContext(correlation)) {
                            if (acceptedClaim != null && runtimeCommandProcessor != null) {
                                runtimeCommandProcessor.process(acceptedClaim);
                            }
                            disposition = triggerExecuteService.execute(triggerId);
                        } catch (Exception exception) {
                            log.error(
                                    "execute trigger fail, triggerId={}, diagnostic={}",
                                    triggerId,
                                    SafeDiagnostic.describe(exception));
                        } finally {
                            try {
                                finishWork(acceptedClaim, disposition);
                            } finally {
                                clearLocalClaim(triggerId, acceptedClaim, claimSlotReserved);
                            }
                        }
                    });
        } catch (RejectedExecutionException exception) {
            clearLocalClaim(triggerId, acceptedClaim, claimSlotReserved);
            if (acceptedClaim != null) {
                workQueue.deferUnstarted(
                        acceptedClaim, "Trigger execution rejected by capacity limit");
                log.warn("trigger work deferred by capacity limit, triggerId={}", triggerId);
                throw exception;
            }
            if (isDurableEventTrigger(triggerId)) {
                log.warn(
                        "event trigger execution deferred by capacity limit, " + "triggerId={}",
                        triggerId);
                throw exception;
            }
            triggerRuntimeService.completeReason(
                    triggerId, TriggerStatus.FAILED, CoreMessage.TRIGGER_CAPACITY);
            throw exception;
        }
    }

    private void clearLocalClaim(Long triggerId, TriggerWorkClaim claim, boolean slotReserved) {
        try {
            if (claim != null) {
                fenceCoordinator.deactivate(claim);
            }
        } finally {
            activeClaims.remove(triggerId);
            dispatchedTriggerIds.remove(triggerId);
            releaseClaimSlot(slotReserved);
            if (executionManager != null) {
                executionManager.clearTriggerCancellation(triggerId);
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.runtime.work-queue.scan-interval:1s}")
    public void scanPersistentWork() {
        if (!persistentQueueEnabled() || !runtimeState.isReady() || !workerRoleEnabled()) {
            return;
        }
        long now = System.nanoTime();
        if (now < nextWorkScanNanos) {
            return;
        }
        int capacity =
                appProperties.getRuntime().getWorkQueue().getMaxInflightRoots()
                        - inflightClaimSlots.get();
        if (capacity <= 0) {
            lastScanNanos = System.nanoTime();
            lastScanAt = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
            scheduleNextWorkScan(false);
            return;
        }
        List<Long> triggerIds = workQueue.listDispatchableTriggerIds(capacity);
        lastScanNanos = System.nanoTime();
        lastScanAt = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        scheduleNextWorkScan(!triggerIds.isEmpty());
        for (Long triggerId : triggerIds) {
            try {
                handle(new TriggerCommand(triggerId));
            } catch (RejectedExecutionException exception) {
                return;
            } catch (RuntimeException exception) {
                log.error(
                        "dispatch persisted trigger work failed, triggerId={}",
                        triggerId,
                        SafeDiagnostic.sanitized(exception));
            }
        }
    }

    private boolean reserveClaimSlot() {
        int maximum = appProperties.getRuntime().getWorkQueue().getMaxInflightRoots();
        while (true) {
            int current = inflightClaimSlots.get();
            if (current >= maximum) {
                return false;
            }
            if (inflightClaimSlots.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    private void releaseClaimSlot(boolean reserved) {
        if (reserved) {
            inflightClaimSlots.decrementAndGet();
        }
    }

    private void scheduleNextWorkScan(boolean foundWork) {
        AppProperties.WorkQueue properties = appProperties.getRuntime().getWorkQueue();
        if (foundWork) {
            consecutiveEmptyScans = 0;
            nextWorkScanNanos = 0L;
            return;
        }
        int exponent = Math.min(consecutiveEmptyScans++, 20);
        long baseMillis = Math.max(1L, properties.getScanInterval().toMillis());
        long maximumMillis = Math.max(baseMillis, properties.getMaxIdleScanInterval().toMillis());
        long multiplier = 1L << exponent;
        long delayMillis = Math.min(maximumMillis, saturatingMultiply(baseMillis, multiplier));
        long jitterBound = properties.getScanJitter().toMillis();
        if (jitterBound > 0) {
            delayMillis =
                    Math.min(
                            maximumMillis,
                            delayMillis + ThreadLocalRandom.current().nextLong(jitterBound + 1));
        }
        nextWorkScanNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMillis);
    }

    private long saturatingMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    @Scheduled(fixedDelayString = "${app.runtime.work-queue.renew-interval:5s}")
    public void renewActiveWork() {
        if (!persistentQueueEnabled()
                || runtimeState == null
                || !runtimeState.isReady()
                || !workerRoleEnabled()) {
            return;
        }
        for (TriggerWorkClaim claim : activeClaims.values()) {
            try {
                if (!workQueue.renew(claim)) {
                    renewalFailures.incrementAndGet();
                    lastRenewalFailureNanos = System.nanoTime();
                    loseWorkOwnership(claim, "renewal was rejected", null);
                }
            } catch (RuntimeException exception) {
                renewalFailures.incrementAndGet();
                lastRenewalFailureNanos = System.nanoTime();
                loseWorkOwnership(claim, "renewal raised an error", exception);
            }
        }
        lastRenewNanos = System.nanoTime();
        lastRenewAt = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
    }

    @Override
    public void onRuntimeFenced(String reason) {
        for (TriggerWorkClaim claim : List.copyOf(activeClaims.values())) {
            loseWorkOwnership(claim, "was cancelled because the runtime was fenced", null);
        }
    }

    private void loseWorkOwnership(
            TriggerWorkClaim claim, String reason, RuntimeException exception) {
        activeClaims.remove(claim.triggerId(), claim);
        fenceCoordinator.deactivate(claim);
        executionManager.cancelTrigger(claim.triggerId());
        coordinationObserver.recordEvent("work", "fence-rejected");
        if (exception == null) {
            log.error(
                    "trigger work lease {}, triggerId={}, owner={}",
                    reason,
                    claim.triggerId(),
                    claim.leaseOwner());
        } else {
            log.error(
                    "trigger work lease {}, triggerId={}, owner={}",
                    reason,
                    claim.triggerId(),
                    claim.leaseOwner(),
                    SafeDiagnostic.sanitized(exception));
        }
    }

    private void finishWork(TriggerWorkClaim claim, TriggerExecutionDisposition disposition) {
        if (claim == null) {
            return;
        }
        boolean updated =
                disposition == TriggerExecutionDisposition.COMPLETED
                        ? workQueue.complete(claim)
                        : disposition == TriggerExecutionDisposition.SUSPENDED
                                ? workQueue.suspend(claim)
                                : workQueue.retry(
                                        claim, "Trigger execution raised an unexpected error");
        if (!updated) {
            coordinationObserver.recordEvent("work", "fence-rejected");
            log.error(
                    "trigger work completion lost its lease, triggerId={}, owner={}",
                    claim.triggerId(),
                    claim.leaseOwner());
        }
    }

    private boolean persistentQueueEnabled() {
        return workQueue != null
                && appProperties != null
                && appProperties.getRuntime().getMode().isPersistent();
    }

    private boolean workerRoleEnabled() {
        return appProperties != null
                && appProperties.getRuntime().getRoles().contains(RuntimeRole.WORKER);
    }

    private boolean isDurableEventTrigger(Long triggerId) {
        var trigger = triggerRuntimeService.getTrigger(triggerId);
        return trigger != null && trigger.getEventId() != null;
    }

    private String resolveLeaseOwner(AppProperties properties) {
        String configured = properties.getRuntime().getInstanceName();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return hostName() + ":" + ProcessHandle.current().pid();
    }

    private String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception exception) {
            return "unknown";
        }
    }
}
