package com.ifelio.ijob.infra.coordination.maintenance;

import java.time.LocalDateTime;

public record MaintenanceLeaseClaim(
        String leaseName,
        String leaseOwner,
        String leaseToken,
        long fenceToken,
        LocalDateTime leaseDeadline) {}
