// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.core.execute.ExecutionHandle;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.instance.InstanceLogService;
import com.ifielsc.ijob.core.instance.InstanceService;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class TaskContextResourceTest {
    @Test
    void cancellationIsOnceExplicitAndIndependentOfCleanup() throws Exception {
        var context = new TaskContext(1L, null, null, null, null);
        var calls = new java.util.ArrayList<CancellationReason>();
        var removed =
                context.onCancellation(
                        reason -> {
                            throw new AssertionError("Unregistered");
                        });
        removed.close();
        context.onCancellation(calls::add);
        context.cleanupResources();
        assertTrue(calls.isEmpty());
        context.cancel(CancellationReason.TIMEOUT);
        context.cancel(CancellationReason.STOP);
        assertEquals(java.util.List.of(CancellationReason.TIMEOUT), calls);
        context.onCancellation(calls::add);
        assertEquals(
                java.util.List.of(CancellationReason.TIMEOUT, CancellationReason.TIMEOUT), calls);
    }

    @Test
    void constructorShouldKeepCollaboratorsAndInputsInTheirPublicAccessors() {
        ExecutionHandle handle = new ExecutionHandle(1L);
        InstanceService instanceService = new InstanceService(null, null);
        InstanceLogService instanceLogService = new InstanceLogService(null);
        VariableResolver variableResolver = new VariableResolver();

        TaskContext context =
                new TaskContext(
                        1L,
                        handle,
                        instanceService,
                        instanceLogService,
                        variableResolver,
                        Map.of("input", "value"));

        assertEquals(1L, context.getInstanceId());
        assertSame(handle, context.getHandle());
        assertSame(instanceService, context.getInstanceService());
        assertSame(instanceLogService, context.getInstanceLogService());
        assertSame(variableResolver, context.getVariableResolver());
        assertEquals(Map.of("input", "value"), context.getInputVariables());
    }

    @Test
    void resetAttemptVariablesShouldRestoreImmutableInputs() {
        TaskContext context =
                new TaskContext(1L, null, null, null, null, Map.of("input", "original"));
        context.putVariable("input", "attempt-value");
        context.putVariable("output", "temporary");

        context.resetAttemptVariables();

        assertEquals("original", context.getVariable("input"));
        assertNull(context.getVariable("output"));
        assertEquals(Map.of("input", "original"), context.getInputVariables());
        assertEquals(Map.of(), context.getOutputVariables());
    }

    @Test
    void outputVariablesShouldTrackRemovalAndProtectBuiltIns() {
        TaskContext context =
                new TaskContext(1L, null, null, null, null, Map.of("input", "original"));

        context.putVariable("result", 7);
        context.removeVariable("input");

        assertEquals(7, context.getOutputVariables().get("result"));
        assertTrue(context.getOutputVariables().containsKey("input"));
        assertNull(context.getOutputVariables().get("input"));
        assertThrows(
                IllegalArgumentException.class, () -> context.putVariable("ijob.trigger.id", 99L));
    }

    @Test
    void resourceRegisteredDuringCleanupShouldRemainForNextCleanup() throws Exception {
        TaskContext context = new TaskContext(1L, null, null, null, null);
        CountDownLatch firstCloseStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstClose = new CountDownLatch(1);
        AtomicBoolean secondClosed = new AtomicBoolean();
        context.registerResource(
                () -> {
                    firstCloseStarted.countDown();
                    releaseFirstClose.await();
                });
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> cleanup = executor.submit(context::cleanupResources);
            assertTrue(firstCloseStarted.await(2, TimeUnit.SECONDS));
            context.registerResource(() -> secondClosed.set(true));
            releaseFirstClose.countDown();
            cleanup.get(2, TimeUnit.SECONDS);

            assertFalse(secondClosed.get());
            context.cleanupResources();
            assertTrue(secondClosed.get());
        } finally {
            releaseFirstClose.countDown();
            executor.shutdownNow();
        }
    }
}
