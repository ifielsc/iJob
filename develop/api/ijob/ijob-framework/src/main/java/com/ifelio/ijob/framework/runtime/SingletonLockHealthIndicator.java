package com.ifelio.ijob.framework.runtime;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("singletonLock")
public class SingletonLockHealthIndicator implements HealthIndicator {
    private final SingletonDatabaseLock singletonDatabaseLock;

    public SingletonLockHealthIndicator(SingletonDatabaseLock singletonDatabaseLock) {
        this.singletonDatabaseLock = singletonDatabaseLock;
    }

    @Override
    public Health health() {
        if (!singletonDatabaseLock.required()) {
            return Health.up().withDetail("required", false).build();
        }
        return singletonDatabaseLock.isHeld()
                ? Health.up().withDetail("required", true).build()
                : Health.down().withDetail("required", true).build();
    }
}
