package com.ifielsc.ijob.framework.database.lock;

import com.ifielsc.ijob.common.config.AppProperties.PlatformDatabaseVendor;

public interface SingletonLockStrategy {
    PlatformDatabaseVendor vendor();

    void acquire(String lockName);

    boolean isHeld(String lockName, int ownershipCheckTimeoutSeconds);

    void release(String lockName);
}
