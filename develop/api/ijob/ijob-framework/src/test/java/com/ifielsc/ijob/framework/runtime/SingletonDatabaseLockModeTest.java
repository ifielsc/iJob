// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.framework.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.PlatformDatabaseVendor;
import com.ifielsc.ijob.common.config.AppProperties.RuntimeMode;
import com.ifielsc.ijob.framework.database.lock.SingletonLockStrategy;

import org.junit.jupiter.api.Test;

class SingletonDatabaseLockModeTest {
    @Test
    void onlyStandaloneShouldRequireTheProcessExclusiveLock() {
        AppProperties properties = new AppProperties();
        SingletonDatabaseLock lock =
                new SingletonDatabaseLock(java.util.List.of(new NoOpLockStrategy()), properties);

        properties.getRuntime().setMode(RuntimeMode.DEVELOPMENT);
        assertFalse(lock.required());
        assertTrue(lock.isHeld());

        properties.getRuntime().setMode(RuntimeMode.CLUSTER);
        assertFalse(lock.required());
        assertTrue(lock.isHeld());

        properties.getRuntime().setMode(RuntimeMode.STANDALONE);
        assertTrue(lock.required());
    }

    private static class NoOpLockStrategy implements SingletonLockStrategy {
        @Override
        public PlatformDatabaseVendor vendor() {
            return PlatformDatabaseVendor.MYSQL;
        }

        @Override
        public void acquire(String lockName) {}

        @Override
        public boolean isHeld(String lockName, int ownershipCheckTimeoutSeconds) {
            return false;
        }

        @Override
        public void release(String lockName) {}
    }
}
