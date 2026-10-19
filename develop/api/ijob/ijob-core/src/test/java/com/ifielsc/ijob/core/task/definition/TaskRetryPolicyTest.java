package com.ifielsc.ijob.core.task.definition;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TaskRetryPolicyTest {
    @Test
    void boundariesAndTotalDelayAreCheckedWithoutOverflow() {
        assertDoesNotThrow(() -> TaskRetryPolicy.validate(0, 0));
        assertDoesNotThrow(() -> TaskRetryPolicy.validate(100, 0));
        assertDoesNotThrow(() -> TaskRetryPolicy.validate(24, 3600));
        assertDoesNotThrow(() -> TaskRetryPolicy.validate(100, 864));
        for (int[] input :
                new int[][] {
                    {-1, 0},
                    {0, -1},
                    {101, 0},
                    {0, 3601},
                    {25, 3600},
                    {100, 865},
                    {Integer.MAX_VALUE, Integer.MAX_VALUE}
                }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TaskRetryPolicy.validate(input[0], input[1]));
        }
    }
}
