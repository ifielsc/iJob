package com.ifielsc.ijob.core.concurrency;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.exception.StopException;
import com.ifielsc.ijob.common.observability.CoordinationObserver;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.common.runtime.RuntimeFenceListener;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.core.execute.ExecutionManager;
import com.ifielsc.ijob.infra.coordination.permit.DatabaseConcurrencyPermitService;
import com.ifielsc.ijob.infra.coordination.permit.DistributedPermit;
import com.ifielsc.ijob.infra.coordination.permit.PermitRequirement;
import com.ifielsc.ijob.infra.coordination.work.ExecutionFenceCoordinator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Service
@lombok.extern.slf4j.Slf4j
public class LockService implements RuntimeFenceListener {
    private final ConcurrentMap<String, LocalScope> localScopes = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, LockHandle> distributedHandles = new ConcurrentHashMap<>();
    private final DatabaseConcurrencyPermitService distributedPermitService;
    private final ExecutionFenceCoordinator fenceCoordinator;
    private final ExecutionManager executionManager;
    private final AppProperties appProperties;
    private final CoordinationObserver coordinationObserver;
    private final ConcurrencyRequirementResolver requirementResolver;
    private final AtomicLong waitingCount = new AtomicLong();

    @Autowired
    public LockService(
            DatabaseConcurrencyPermitService distributedPermitService,
            ExecutionFenceCoordinator fenceCoordinator,
            ExecutionManager executionManager,
            AppProperties appProperties,
            Optional<CoordinationObserver> coordinationObserver,
            ConcurrencyRequirementResolver requirementResolver) {
        this.distributedPermitService = distributedPermitService;
        this.fenceCoordinator = fenceCoordinator;
        this.executionManager = executionManager;
        this.appProperties = appProperties;
        this.coordinationObserver = coordinationObserver.orElse(CoordinationObserver.NOOP);
        this.requirementResolver =
                java.util.Objects.requireNonNull(requirementResolver, "requirementResolver");
    }

    public LockHandle acquireTask(Long instanceId, Long taskId, PluginType pluginType) {
        return acquireTask(instanceId, taskId, pluginType, () -> {});
    }

    public LockHandle acquireTask(
            Long instanceId, Long taskId, PluginType pluginType, Runnable onWaiting) {
        if (taskId == null || taskId <= 0 || pluginType == null) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "Task concurrency identity is invalid");
        }
        List<ConcurrencyRequirement> requirements = requirementResolver.resolve(taskId, pluginType);
        if (requirements.isEmpty()) return null;
        WaitObservation observation = new WaitObservation(onWaiting);
        try {
            LockHandle handle =
                    distributedEnabled()
                            ? acquireDistributed(instanceId, requirements, observation)
                            : acquireLocal(instanceId, requirements, observation);
            observation.record("acquired");
            return handle;
        } catch (PermitWaitTimeoutException exception) {
            observation.record("timeout");
            throw exception;
        } catch (StopException exception) {
            observation.record("cancelled");
            throw exception;
        } finally {
            observation.close();
        }
    }

    private LockHandle acquireDistributed(
            Long instanceId,
            List<ConcurrencyRequirement> requirements,
            WaitObservation observation) {
        if (instanceId == null || instanceId <= 0) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "Task instance identity is invalid");
        }
        long backoffMillis = retryInterval().toMillis();
        while (true) {
            checkWaitDeadline(observation);
            List<PermitRequirement> permitRequirements =
                    requirements.stream()
                            .map(
                                    value ->
                                            new PermitRequirement(
                                                    value.scopeKey(), value.maxConcurrency()))
                            .toList();
            List<DistributedPermit> permits =
                    distributedPermitService.tryAcquireAll(instanceId, permitRequirements);
            if (permits != null) {
                LockHandle handle = new LockHandle(instanceId, List.of(), permits);
                if (distributedHandles.putIfAbsent(instanceId, handle) != null) {
                    releaseDistributed(permits);
                    throw BusinessException.localized(
                            ResultCode.SYSTEM_ERROR, "Task already owns concurrency permits");
                }
                return handle;
            }
            observation.begin();
            if (!distributedPermitService.isAdmissible(instanceId)) throw new StopException();
            sleepWithJitter(backoffMillis);
            backoffMillis = nextBackoff(backoffMillis);
        }
    }

    private LockHandle acquireLocal(
            Long instanceId,
            List<ConcurrencyRequirement> requirements,
            WaitObservation observation) {
        long backoffMillis = retryInterval().toMillis();
        while (true) {
            checkWaitDeadline(observation);
            if (distributedPermitService != null
                    && instanceId != null
                    && !distributedPermitService.isAdmissible(instanceId)) {
                throw new StopException();
            }
            List<LockHandle.LocalPermit> acquired = new ArrayList<>();
            try {
                for (ConcurrencyRequirement requirement : requirements) {
                    LocalScope scope =
                            localScopes.compute(
                                    requirement.scopeKey(),
                                    (key, current) -> configureLocalScope(current, requirement));
                    if (!scope.semaphore.tryAcquire()) {
                        releaseLocal(acquired);
                        acquired = null;
                        break;
                    }
                    acquired.add(
                            new LockHandle.LocalPermit(requirement.scopeKey(), scope.semaphore));
                }
                if (acquired != null) return new LockHandle(null, acquired, List.of());
            } catch (RuntimeException | Error failure) {
                if (acquired != null) releaseLocal(acquired);
                throw failure;
            }
            observation.begin();
            sleepWithJitter(backoffMillis);
            backoffMillis = nextBackoff(backoffMillis);
        }
    }

    private LocalScope configureLocalScope(LocalScope current, ConcurrencyRequirement requirement) {
        int maximum = requirement.maxConcurrency();
        long epoch = requirement.policyEpoch();
        if (current == null) return new LocalScope(maximum, epoch);
        synchronized (current) {
            // A waiter from an earlier snapshot may use the newer capacity but must never
            // reconfigure it. Restrictive policy updates remain prohibited while instances run.
            if (epoch < current.epoch) return current;
            current.epoch = epoch;
            if (current.maximum == maximum) return current;
            if (maximum > current.maximum) {
                current.semaphore.release(maximum - current.maximum);
                current.maximum = maximum;
                return current;
            }
            if (current.semaphore.availablePermits() != current.maximum) {
                throw BusinessException.localized(
                        ResultCode.CONFLICT,
                        "Concurrency limit cannot decrease while permits are active");
            }
            return new LocalScope(maximum, epoch);
        }
    }

    public void release(LockHandle handle) {
        if (handle == null) return;
        if (!handle.distributedPermits().isEmpty()) {
            distributedHandles.remove(handle.ownerInstanceId(), handle);
            releaseDistributed(handle.distributedPermits());
        } else {
            releaseLocal(handle.localPermits());
        }
    }

    public boolean hasActiveLocalPermits(String scopeKey) {
        LocalScope scope = localScopes.get(scopeKey);
        if (scope == null) return false;
        synchronized (scope) {
            return scope.semaphore.availablePermits() != scope.maximum;
        }
    }

    private void releaseLocal(List<LockHandle.LocalPermit> permits) {
        for (int index = permits.size() - 1; index >= 0; index--) {
            permits.get(index).semaphore().release();
        }
    }

    private void releaseDistributed(List<DistributedPermit> permits) {
        for (int index = permits.size() - 1; index >= 0; index--) {
            distributedPermitService.release(permits.get(index));
        }
    }

    @Scheduled(fixedDelayString = "${app.runtime.concurrency-permit.renew-interval:5s}")
    public void renewDistributedPermits() {
        if (!distributedEnabled()
                || !appProperties
                        .getRuntime()
                        .getRoles()
                        .contains(AppProperties.RuntimeRole.WORKER)) return;
        for (LockHandle handle : distributedHandles.values()) {
            for (DistributedPermit permit : handle.distributedPermits()) {
                try {
                    if (!distributedPermitService.renew(permit)) {
                        log.warn(
                                "Permit renewal rejected, triggerId={}, rootInstanceId={},"
                                        + " instanceId={}, scope={}, slot={}, fence={}",
                                permit.ownerTriggerId(),
                                permit.ownerRootInstanceId(),
                                permit.ownerInstanceId(),
                                permit.scopeKey(),
                                permit.slotNo(),
                                permit.permitFence());
                        losePermits(handle, "fence-rejected");
                        break;
                    }
                } catch (RuntimeException exception) {
                    log.warn(
                            "Permit renewal failed, triggerId={}, rootInstanceId={}, instanceId={},"
                                    + " scope={}, slot={}, fence={}, diagnostic={}",
                            permit.ownerTriggerId(),
                            permit.ownerRootInstanceId(),
                            permit.ownerInstanceId(),
                            permit.scopeKey(),
                            permit.slotNo(),
                            permit.permitFence(),
                            com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.describe(exception));
                    losePermits(handle, "lease-renewal-error");
                    break;
                }
            }
        }
    }

    @Override
    public void onRuntimeFenced(String reason) {
        for (LockHandle handle : distributedHandles.values()) {
            losePermits(handle, "runtime-fenced");
        }
    }

    private void losePermits(LockHandle handle, String event) {
        coordinationObserver.recordEvent("permit", event);
        if (distributedHandles.remove(handle.ownerInstanceId(), handle)
                && executionManager != null) {
            executionManager.stop(handle.ownerInstanceId());
        }
    }

    private boolean distributedEnabled() {
        return distributedPermitService != null
                && fenceCoordinator != null
                && fenceCoordinator.isEnforced();
    }

    private Duration retryInterval() {
        return appProperties == null
                ? Duration.ofMillis(100)
                : appProperties.getRuntime().getConcurrencyPermit().getRetryInterval();
    }

    public LocalDateTime permitWaitDeadline() {
        return LocalDateTime.now().plus(permitWaitTimeout());
    }

    private Duration permitWaitTimeout() {
        return appProperties == null
                ? Duration.ofMinutes(5)
                : appProperties.getRuntime().getConcurrencyPermit().getWaitTimeout();
    }

    private void checkWaitDeadline(WaitObservation observation) {
        if (!observation.started) return;
        Duration timeout = permitWaitTimeout();
        if (timeout == null
                || timeout.isZero()
                || timeout.isNegative()
                || System.nanoTime() - observation.startedAt >= timeout.toNanos()) {
            throw new PermitWaitTimeoutException();
        }
    }

    private long nextBackoff(long currentMillis) {
        long maximum =
                appProperties == null
                        ? 2_000L
                        : appProperties
                                .getRuntime()
                                .getConcurrencyPermit()
                                .getMaxRetryInterval()
                                .toMillis();
        return Math.min(maximum, currentMillis > Long.MAX_VALUE / 2 ? maximum : currentMillis * 2);
    }

    private void sleepWithJitter(long backoffMillis) {
        long jitter =
                appProperties == null
                        ? 100L
                        : appProperties
                                .getRuntime()
                                .getConcurrencyPermit()
                                .getRetryJitter()
                                .toMillis();
        long randomized =
                jitter <= 0
                        ? backoffMillis
                        : backoffMillis + ThreadLocalRandom.current().nextLong(jitter + 1);
        sleep(Duration.ofMillis(randomized));
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(Math.max(1L, duration.toMillis()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new StopException();
        }
    }

    private static final class LocalScope {
        private int maximum;
        private long epoch;
        private final Semaphore semaphore;

        private LocalScope(int maximum, long epoch) {
            this.maximum = maximum;
            this.epoch = epoch;
            this.semaphore = new Semaphore(maximum, true);
        }
    }

    private final class WaitObservation implements AutoCloseable {
        private final Runnable onWaiting;
        private long startedAt;
        private boolean started;

        private WaitObservation(Runnable onWaiting) {
            this.onWaiting = onWaiting == null ? () -> {} : onWaiting;
        }

        private void begin() {
            if (started) return;
            started = true;
            startedAt = System.nanoTime();
            long waiting = waitingCount.incrementAndGet();
            coordinationObserver.recordState("permit", "waiting", waiting);
            coordinationObserver.recordPermitWaiting(waiting);
            onWaiting.run();
        }

        private void record(String outcome) {
            if (!started) return;
            Duration duration = Duration.ofNanos(System.nanoTime() - startedAt);
            if ("acquired".equals(outcome)) {
                coordinationObserver.recordEvent("permit", "acquired");
                coordinationObserver.recordDelay("permit-wait", duration);
            } else if ("timeout".equals(outcome)) {
                coordinationObserver.recordEvent("permit", "wait-timeout");
            }
            coordinationObserver.recordPermitWait(duration, outcome);
        }

        @Override
        public void close() {
            if (!started) return;
            long remaining = waitingCount.decrementAndGet();
            coordinationObserver.recordState("permit", "waiting", remaining);
            coordinationObserver.recordPermitWaiting(remaining);
        }
    }
}
