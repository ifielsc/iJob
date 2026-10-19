// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.coordination.work;

import com.ifielsc.ijob.common.config.AppProperties;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@Component
public class ExecutionFenceCoordinator {
    private final AppProperties appProperties;
    private final ConcurrentMap<Long, TriggerWorkClaim> activeClaims = new ConcurrentHashMap<>();
    private final ThreadLocal<Map<Long, SuspendedWorkPermit>> suspendedAccess =
            ThreadLocal.withInitial(HashMap::new);

    public ExecutionFenceCoordinator(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public void activate(TriggerWorkClaim claim) {
        TriggerWorkClaim existing = activeClaims.putIfAbsent(claim.triggerId(), claim);
        if (existing != null && !existing.claimToken().equals(claim.claimToken())) {
            throw new IllegalStateException(
                    "Another local execution claim is already active for trigger "
                            + claim.triggerId());
        }
    }

    public void deactivate(TriggerWorkClaim claim) {
        activeClaims.computeIfPresent(
                claim.triggerId(),
                (triggerId, current) ->
                        current.claimToken().equals(claim.claimToken()) ? null : current);
    }

    public TriggerWorkClaim activeClaim(Long triggerId) {
        return activeClaims.get(triggerId);
    }

    public List<TriggerWorkClaim> activeClaims() {
        return List.copyOf(activeClaims.values());
    }

    public ExecutionFencePermit requirePermit(Long triggerId) {
        if (!isEnforced()) {
            return ExecutionFencePermit.unfenced();
        }
        TriggerWorkClaim claim = activeClaims.get(triggerId);
        if (claim != null) {
            return ExecutionFencePermit.claimed(claim);
        }
        SuspendedWorkPermit suspendedPermit = suspendedAccess.get().get(triggerId);
        if (suspendedPermit != null) {
            return ExecutionFencePermit.suspended(suspendedPermit);
        }
        throw new ExecutionOwnershipLostException(triggerId);
    }

    public <T> T withSuspendedAccess(SuspendedWorkPermit permit, Supplier<T> action) {
        if (!isEnforced()) {
            return action.get();
        }
        Map<Long, SuspendedWorkPermit> permits = suspendedAccess.get();
        SuspendedWorkPermit previous = permits.putIfAbsent(permit.triggerId(), permit);
        if (previous != null && previous.fenceToken() != permit.fenceToken()) {
            throw new ExecutionOwnershipLostException(permit.triggerId());
        }
        try {
            return action.get();
        } finally {
            if (previous == null) {
                permits.remove(permit.triggerId(), permit);
            }
            if (permits.isEmpty()) {
                suspendedAccess.remove();
            }
        }
    }

    public void withSuspendedAccess(SuspendedWorkPermit permit, Runnable action) {
        withSuspendedAccess(
                permit,
                () -> {
                    action.run();
                    return null;
                });
    }

    public boolean isEnforced() {
        return appProperties.getRuntime().getMode().isPersistent();
    }
}
