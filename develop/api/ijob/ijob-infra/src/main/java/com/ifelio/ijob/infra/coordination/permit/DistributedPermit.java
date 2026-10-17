package com.ifelio.ijob.infra.coordination.permit;

import java.time.LocalDateTime;

public record DistributedPermit(
        String scopeKey,
        int slotNo,
        Long ownerInstanceId,
        Long ownerRootInstanceId,
        Long ownerTriggerId,
        String leaseOwner,
        String permitToken,
        long permitFence,
        long executionFence,
        LocalDateTime leaseDeadline) {}
