package com.ifielsc.ijob.framework.runtime;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.PlatformDatabaseVendor;
import com.ifielsc.ijob.common.config.AppProperties.RuntimeMode;
import com.ifielsc.ijob.framework.database.lock.SingletonLockStrategy;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SingletonDatabaseLock implements DisposableBean {
    private final List<SingletonLockStrategy> lockStrategies;
    private final AppProperties appProperties;

    public SingletonDatabaseLock(
            List<SingletonLockStrategy> lockStrategies, AppProperties appProperties) {
        this.lockStrategies = lockStrategies;
        this.appProperties = appProperties;
    }

    public synchronized void acquire() {
        if (!required() || isHeld()) {
            return;
        }
        strategy().acquire(appProperties.getRuntime().getSingletonLockName());
    }

    public synchronized boolean isHeld() {
        if (!required()) {
            return true;
        }
        return strategy()
                .isHeld(
                        appProperties.getRuntime().getSingletonLockName(),
                        ownershipCheckTimeoutSeconds());
    }

    public boolean required() {
        return appProperties.getRuntime().getMode() == RuntimeMode.STANDALONE;
    }

    private int ownershipCheckTimeoutSeconds() {
        long seconds = appProperties.getRuntime().getHeartbeatInterval().toSeconds();
        return (int) Math.max(1L, Math.min(5L, seconds));
    }

    @Override
    public synchronized void destroy() {
        PlatformDatabaseVendor vendor = appProperties.getPlatformDatabase().getVendor();
        lockStrategies.stream()
                .filter(candidate -> candidate.vendor() == vendor)
                .findFirst()
                .ifPresent(
                        candidate ->
                                candidate.release(
                                        appProperties.getRuntime().getSingletonLockName()));
    }

    private SingletonLockStrategy strategy() {
        PlatformDatabaseVendor vendor = appProperties.getPlatformDatabase().getVendor();
        return lockStrategies.stream()
                .filter(candidate -> candidate.vendor() == vendor)
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "No singleton lock strategy is available for " + vendor));
    }
}
