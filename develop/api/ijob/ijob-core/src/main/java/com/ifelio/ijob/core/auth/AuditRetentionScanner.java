package com.ifelio.ijob.core.auth;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.common.runtime.ApplicationRuntimeState;
import com.ifelio.ijob.core.maintenance.MaintenanceLeaderCoordinator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.retention", name = "enabled", havingValue = "true")
@lombok.extern.slf4j.Slf4j
public class AuditRetentionScanner {
    private final AuditService audit;
    private final AppProperties properties;
    private final ApplicationRuntimeState state;
    private final MaintenanceLeaderCoordinator leader;

    public AuditRetentionScanner(
            AuditService audit,
            AppProperties properties,
            ApplicationRuntimeState state,
            MaintenanceLeaderCoordinator leader) {
        this.audit = audit;
        this.properties = properties;
        this.state = state;
        this.leader = leader;
    }

    @Scheduled(
            initialDelayString = "${app.retention.initial-delay:1m}",
            fixedDelayString = "${app.retention.interval:1h}")
    public void scan() {
        if (!state.isReady()
                || !properties
                        .getRuntime()
                        .getRoles()
                        .contains(AppProperties.RuntimeRole.MAINTENANCE)) return;
        leader.runIfLeader(
                () -> {
                    var retention = properties.getRetention();
                    var cutoff = AuditService.now().minus(retention.getAudit());
                    int limit = Math.min(500, retention.getBatchSize());
                    int total = 0;
                    for (int i = 0; i < retention.getMaxBatchesPerRun(); i++) {
                        int count = audit.cleanup(cutoff, limit, retention.isDryRun());
                        total += count;
                        if (retention.isDryRun() || count < limit) break;
                    }
                    log.info(
                            "operation audit retention actor=SYSTEM dryRun={} rows={}",
                            retention.isDryRun(),
                            total);
                    return Boolean.TRUE;
                });
    }
}
