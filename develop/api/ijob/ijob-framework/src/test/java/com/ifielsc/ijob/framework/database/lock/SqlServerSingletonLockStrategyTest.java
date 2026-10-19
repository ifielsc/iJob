// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.framework.database.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SqlServerSingletonLockStrategyTest {
    @Test
    void lockResourceShouldBeStableBoundedAndSensitiveToTheFullName() {
        String first = SqlServerSingletonLockStrategy.lockResource("ijob:standalone");

        assertEquals(first, SqlServerSingletonLockStrategy.lockResource("ijob:standalone"));
        assertNotEquals(
                first, SqlServerSingletonLockStrategy.lockResource("ijob:standalone:other"));
        assertNotEquals(first, SqlServerSingletonLockStrategy.lockResource("IJOB:STANDALONE"));
        assertTrue(first.length() <= 255);
    }
}
