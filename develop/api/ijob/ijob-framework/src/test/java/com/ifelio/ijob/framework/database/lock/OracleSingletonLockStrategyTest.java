package com.ifelio.ijob.framework.database.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OracleSingletonLockStrategyTest {
    @Test
    void lockIdShouldBeStableWithinOracleUserLockRangeAndUseTheFullName() {
        int first = OracleSingletonLockStrategy.lockId("ijob:standalone");

        assertEquals(first, OracleSingletonLockStrategy.lockId("ijob:standalone"));
        assertNotEquals(first, OracleSingletonLockStrategy.lockId("ijob:standalone:other"));
        assertNotEquals(first, OracleSingletonLockStrategy.lockId("IJOB:STANDALONE"));
        assertTrue(first >= 0);
        assertTrue(first <= 1_073_741_823);
    }
}
