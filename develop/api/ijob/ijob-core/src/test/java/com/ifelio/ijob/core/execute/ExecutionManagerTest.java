package com.ifelio.ijob.core.execute;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExecutionManagerTest {
    @Test
    void triggerCancellationShouldStopExistingAndFutureHandlesUntilCleared() {
        ExecutionManager manager = new ExecutionManager();
        ExecutionHandle existing = manager.register(11L, 7L);
        ExecutionHandle unrelated = manager.register(12L, 8L);

        manager.cancelTrigger(7L);
        ExecutionHandle late = manager.register(13L, 7L);

        assertTrue(existing.isStopRequested());
        assertTrue(late.isStopRequested());
        assertFalse(unrelated.isStopRequested());

        manager.clearTriggerCancellation(7L);
        assertFalse(manager.register(14L, 7L).isStopRequested());
    }

    @Test
    void runtimeFenceShouldStopAllExistingAndFutureHandles() {
        ExecutionManager manager = new ExecutionManager();
        ExecutionHandle first = manager.register(21L, 3L);
        ExecutionHandle second = manager.register(22L, 4L);

        manager.onRuntimeFenced("ownership lost");

        assertTrue(first.isStopRequested());
        assertTrue(second.isStopRequested());
        assertTrue(manager.register(23L, 5L).isStopRequested());
    }
}
