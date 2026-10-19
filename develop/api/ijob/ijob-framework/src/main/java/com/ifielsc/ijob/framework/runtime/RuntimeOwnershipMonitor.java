package com.ifielsc.ijob.framework.runtime;

import com.ifielsc.ijob.common.runtime.ApplicationRuntimeState;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RuntimeOwnershipMonitor {
    private final SingletonDatabaseLock singletonDatabaseLock;
    private final ApplicationRuntimeState runtimeState;
    private final RuntimeSafetyCoordinator safetyCoordinator;

    public RuntimeOwnershipMonitor(
            SingletonDatabaseLock singletonDatabaseLock,
            ApplicationRuntimeState runtimeState,
            RuntimeSafetyCoordinator safetyCoordinator) {
        this.singletonDatabaseLock = singletonDatabaseLock;
        this.runtimeState = runtimeState;
        this.safetyCoordinator = safetyCoordinator;
    }

    @Scheduled(fixedDelayString = "${app.runtime.heartbeat-interval:5s}")
    public void verifyOwnership() {
        if (!runtimeState.isReady() || !singletonDatabaseLock.required()) {
            return;
        }
        if (!singletonDatabaseLock.isHeld()) {
            safetyCoordinator.fence("Standalone database lock was lost", null);
        }
    }
}
