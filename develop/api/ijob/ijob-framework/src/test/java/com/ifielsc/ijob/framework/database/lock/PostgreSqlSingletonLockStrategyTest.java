// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.framework.database.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class PostgreSqlSingletonLockStrategyTest {
    @Test
    void lockKeyShouldBeStableAndSensitiveToTheFullName() {
        long first = PostgreSqlSingletonLockStrategy.lockKey("ijob:standalone");

        assertEquals(first, PostgreSqlSingletonLockStrategy.lockKey("ijob:standalone"));
        assertNotEquals(first, PostgreSqlSingletonLockStrategy.lockKey("ijob:standalone:other"));
    }
}
