package com.ifelio.ijob.contract.workbench;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Counts and identifiers are decimal strings; node configuration is not cluster health. */
public record WorkbenchDTO(
        LocalDateTime from,
        LocalDateTime to,
        LocalDateTime queriedAt,
        String timeZone,
        String businessZone,
        String total,
        Map<String, String> statuses,
        String successRateDenominator,
        String successRatePercent,
        Map<String, String> currentActiveRoots,
        boolean approvalsVisible,
        String pendingApprovals,
        List<WorkbenchFailureDTO> recentFailures,
        NodeConfiguration node) {
    public record NodeConfiguration(
            String mode,
            List<String> roles,
            boolean workerRoleConfigured,
            boolean schedulerRoleConfigured,
            boolean scheduleEnabled,
            String clusterWorkerHealth) {}
}
