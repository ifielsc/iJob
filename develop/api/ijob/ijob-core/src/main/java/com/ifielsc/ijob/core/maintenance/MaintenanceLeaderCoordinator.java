// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.maintenance;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.RuntimeRole;
import com.ifielsc.ijob.common.observability.CoordinationObserver;
import com.ifielsc.ijob.common.runtime.ApplicationRuntimeState;
import com.ifielsc.ijob.infra.coordination.maintenance.DatabaseMaintenanceLease;
import com.ifielsc.ijob.infra.coordination.maintenance.DatabaseMaintenanceLease.GuardedResult;
import com.ifielsc.ijob.infra.coordination.maintenance.MaintenanceLeaseClaim;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Optional;
import java.util.function.Supplier;

@Component
public class MaintenanceLeaderCoordinator implements DisposableBean {
    public static final String LEASE_NAME = "runtime-maintenance";

    private final DatabaseMaintenanceLease databaseLease;
    private final AppProperties appProperties;
    private final ApplicationRuntimeState runtimeState;
    private final CoordinationObserver observer;
    private final String leaseOwner;
    private volatile MaintenanceLeaseClaim claim;

    @Autowired
    public MaintenanceLeaderCoordinator(
            DatabaseMaintenanceLease databaseLease,
            AppProperties appProperties,
            ApplicationRuntimeState runtimeState,
            Optional<CoordinationObserver> observer) {
        this.databaseLease = databaseLease;
        this.appProperties = appProperties;
        this.runtimeState = runtimeState;
        this.observer = observer.orElse(CoordinationObserver.NOOP);
        this.leaseOwner = resolveOwner(appProperties);
    }

    public void start() {
        if (required() && claim == null) {
            acquire();
        }
    }

    @Scheduled(fixedDelayString = "${app.runtime.maintenance.renew-interval:10s}")
    public void heartbeat() {
        if (!required() || !runtimeState.isReady()) {
            return;
        }
        MaintenanceLeaseClaim current = claim;
        if (current == null) {
            acquire();
            return;
        }
        boolean renewed;
        try {
            renewed = databaseLease.renew(current, properties().getLeaseDuration());
        } catch (RuntimeException exception) {
            claim = null;
            observer.recordLease(LEASE_NAME, false, current.fenceToken());
            observer.recordEvent("maintenance", "lease-renewal-error");
            return;
        }
        if (!renewed) {
            claim = null;
            observer.recordLease(LEASE_NAME, false, current.fenceToken());
            observer.recordEvent("maintenance", "lease-lost");
        } else {
            observer.recordLease(LEASE_NAME, true, current.fenceToken());
        }
    }

    public <T> Optional<T> runIfLeader(Supplier<T> action) {
        if (!appProperties.getRuntime().getMode().isPersistent()) {
            return Optional.ofNullable(action.get());
        }
        if (!properties().isEnabled()) {
            return Optional.empty();
        }
        MaintenanceLeaseClaim current = claim;
        if (current == null) {
            return Optional.empty();
        }
        GuardedResult<T> result = databaseLease.runIfHeld(current, action);
        if (!result.held()) {
            claim = null;
            observer.recordLease(LEASE_NAME, false, current.fenceToken());
            observer.recordEvent("maintenance", "fence-rejected");
            return Optional.empty();
        }
        return Optional.ofNullable(result.value());
    }

    public boolean isLeader() {
        return claim != null;
    }

    @Override
    public void destroy() {
        MaintenanceLeaseClaim current = claim;
        claim = null;
        if (current != null) {
            databaseLease.release(current);
            observer.recordLease(LEASE_NAME, false, current.fenceToken());
        }
    }

    private void acquire() {
        MaintenanceLeaseClaim acquired =
                databaseLease.claim(LEASE_NAME, leaseOwner, properties().getLeaseDuration());
        if (acquired == null) {
            observer.recordLease(LEASE_NAME, false, 0);
            return;
        }
        claim = acquired;
        observer.recordLease(LEASE_NAME, true, acquired.fenceToken());
        observer.recordEvent("maintenance", acquired.fenceToken() > 1 ? "takeover" : "acquired");
    }

    private boolean required() {
        return appProperties.getRuntime().getMode().isPersistent()
                && properties().isEnabled()
                && appProperties.getRuntime().getRoles().contains(RuntimeRole.MAINTENANCE);
    }

    private AppProperties.Maintenance properties() {
        return appProperties.getRuntime().getMaintenance();
    }

    private String resolveOwner(AppProperties properties) {
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
