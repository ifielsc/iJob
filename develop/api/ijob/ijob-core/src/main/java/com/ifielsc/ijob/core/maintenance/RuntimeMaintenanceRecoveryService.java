package com.ifielsc.ijob.core.maintenance;

import com.ifielsc.ijob.common.observability.CoordinationObserver;
import com.ifielsc.ijob.contract.enums.TriggerStatus;
import com.ifielsc.ijob.core.error.CoreMessage;
import com.ifielsc.ijob.core.execute.InstanceRuntimeService;
import com.ifielsc.ijob.core.execute.task.TaskResult;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.core.trigger.TriggerExecuteService;
import com.ifielsc.ijob.core.trigger.TriggerRuntimeService;
import com.ifielsc.ijob.infra.coordination.work.DatabaseTriggerWorkQueue;
import com.ifielsc.ijob.infra.coordination.work.ExecutionFenceCoordinator;
import com.ifielsc.ijob.infra.coordination.work.TriggerWorkClaim;
import com.ifielsc.ijob.infra.entity.runtime.Instance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class RuntimeMaintenanceRecoveryService {

    private final DatabaseTriggerWorkQueue workQueue;
    private final ExecutionFenceCoordinator fenceCoordinator;
    private final InstanceService instanceService;
    private final InstanceRuntimeService instanceRuntimeService;
    private final TriggerExecuteService triggerExecuteService;
    private final TriggerRuntimeService triggerRuntimeService;
    private final CoordinationObserver observer;

    @Autowired
    public RuntimeMaintenanceRecoveryService(
            DatabaseTriggerWorkQueue workQueue,
            ExecutionFenceCoordinator fenceCoordinator,
            InstanceService instanceService,
            InstanceRuntimeService instanceRuntimeService,
            TriggerExecuteService triggerExecuteService,
            TriggerRuntimeService triggerRuntimeService,
            Optional<CoordinationObserver> observer) {
        this.workQueue = workQueue;
        this.fenceCoordinator = fenceCoordinator;
        this.instanceService = instanceService;
        this.instanceRuntimeService = instanceRuntimeService;
        this.triggerExecuteService = triggerExecuteService;
        this.triggerRuntimeService = triggerRuntimeService;
        this.observer = observer.orElse(CoordinationObserver.NOOP);
    }

    public int recoverTimedOutBatch(String leaseOwner, int limit) {
        int recovered = 0;
        for (Long triggerId : workQueue.listTimedOutTriggerIds(limit)) {
            TriggerWorkClaim claim = workQueue.claimTimedOut(triggerId, leaseOwner);
            if (claim != null) {
                settle(claim, true);
                recovered++;
            }
        }
        return recovered;
    }

    public int recoverDeadOrphanBatch(String leaseOwner, Duration orphanGrace, int limit) {
        int recovered = 0;
        for (Long triggerId : workQueue.listDeadOrphanTriggerIds(orphanGrace, limit)) {
            TriggerWorkClaim claim = workQueue.claimDeadOrphan(triggerId, leaseOwner, orphanGrace);
            if (claim != null) {
                settle(claim, false);
                recovered++;
            }
        }
        return recovered;
    }

    public int expireQueuedBatch(String leaseOwner, int limit) {
        int expired = 0;
        for (Long triggerId : workQueue.listExpiredTriggerIds(limit)) {
            TriggerWorkClaim claim = workQueue.claimExpired(triggerId, leaseOwner);
            if (claim == null) {
                continue;
            }
            fenceCoordinator.activate(claim);
            try {
                triggerRuntimeService.completeReason(
                        triggerId, TriggerStatus.SKIPPED, CoreMessage.QUEUE_DELAY);
                if (!workQueue.complete(claim)) {
                    throw new IllegalStateException("Expired work completion lost ownership");
                }
                observer.recordEvent("runtime-recovery", "queue-expired");
                expired++;
            } finally {
                fenceCoordinator.deactivate(claim);
            }
        }
        return expired;
    }

    private void settle(TriggerWorkClaim claim, boolean timedOut) {
        fenceCoordinator.activate(claim);
        try {
            Instance root = instanceService.getRootByTriggerId(claim.triggerId());
            if (root == null) {
                if (!workQueue.complete(claim)) {
                    throw new IllegalStateException("Missing-root work completion lost ownership");
                }
                return;
            }
            instanceService.adoptRoot(root.getId(), claim);
            settleTree(root.getId(), timedOut, new HashSet<>());
            triggerExecuteService.completeTrigger(claim.triggerId(), root.getId());
            if (!workQueue.complete(claim)) {
                throw new IllegalStateException("Recovered work completion lost ownership");
            }
            observer.recordEvent("runtime-recovery", timedOut ? "timed-out" : "orphan-failed");
        } finally {
            fenceCoordinator.deactivate(claim);
        }
    }

    private void settleTree(Long instanceId, boolean timedOut, Set<Long> visited) {
        if (instanceId == null || !visited.add(instanceId)) {
            return;
        }
        for (Instance child : instanceService.listChildren(instanceId)) {
            settleTree(child.getId(), timedOut, visited);
        }
        Instance current = instanceService.getInstance(instanceId);
        if (current != null && !current.getStatus().isFinished()) {
            if (timedOut) {
                instanceRuntimeService.completeTask(
                        instanceId, TaskResult.timeout(CoreMessage.RUNTIME_DEADLINE));
            } else {
                instanceRuntimeService.completeTask(
                        instanceId, TaskResult.fail(CoreMessage.RUNTIME_ORPHANED));
            }
        }
    }
}
