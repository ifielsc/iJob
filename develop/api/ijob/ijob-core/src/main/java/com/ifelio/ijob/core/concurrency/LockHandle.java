package com.ifelio.ijob.core.concurrency;

import com.ifelio.ijob.infra.coordination.permit.DistributedPermit;

import java.util.List;
import java.util.concurrent.Semaphore;

public record LockHandle(
        Long ownerInstanceId,
        List<LocalPermit> localPermits,
        List<DistributedPermit> distributedPermits) {
    public LockHandle {
        localPermits = localPermits == null ? List.of() : List.copyOf(localPermits);
        distributedPermits =
                distributedPermits == null ? List.of() : List.copyOf(distributedPermits);
    }

    public record LocalPermit(String scopeKey, Semaphore semaphore) {}
}
