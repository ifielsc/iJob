// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class FutureWindowTest {
    private final List<ExecutorService> executors = new ArrayList<>();

    @AfterEach
    void cleanup() {
        executors.forEach(ExecutorService::shutdownNow);
    }

    @Test
    void flushShouldWaitForCurrentFutureAndClearWindow() throws Exception {
        FutureWindow window = new FutureWindow(2, 2, TimeUnit.SECONDS);
        CompletableFuture<Void> first = new CompletableFuture<>();
        window.add(first);
        ExecutorService executor = register(Executors.newSingleThreadExecutor());

        Future<?> flush = executor.submit(window::flush);
        assertFalse(flush.isDone());
        first.complete(null);
        flush.get(2, TimeUnit.SECONDS);

        assertTrue(window.isEmpty());
    }

    @Test
    void reachingCapacityShouldApplyBackpressure() throws Exception {
        FutureWindow window = new FutureWindow(1, 2, TimeUnit.SECONDS);
        CompletableFuture<Void> pending = new CompletableFuture<>();
        ExecutorService executor = register(Executors.newSingleThreadExecutor());

        Future<?> add = executor.submit(() -> window.add(pending));
        assertFalse(add.isDone());
        pending.complete(null);
        add.get(2, TimeUnit.SECONDS);

        assertEquals(0, window.size());
    }

    @Test
    void failureShouldPropagateAndCancelSibling() {
        FutureWindow window = new FutureWindow(3, 2, TimeUnit.SECONDS);
        CompletableFuture<Void> failed =
                CompletableFuture.failedFuture(new IllegalStateException("broker rejected"));
        CompletableFuture<Void> sibling = new CompletableFuture<>();
        window.add(failed);
        window.add(sibling);

        IllegalStateException failure = assertThrows(IllegalStateException.class, window::flush);

        assertEquals("publish failed", failure.getMessage());
        assertTrue(sibling.isCancelled());
        assertTrue(window.isEmpty());
    }

    @Test
    void timeoutShouldCancelUnfinishedFuture() {
        FutureWindow window = new FutureWindow(2, 20, TimeUnit.MILLISECONDS);
        CompletableFuture<Void> pending = new CompletableFuture<>();
        window.add(pending);

        IllegalStateException failure = assertThrows(IllegalStateException.class, window::flush);

        assertEquals("publish timeout", failure.getMessage());
        assertTrue(pending.isCancelled());
    }

    @Test
    void cancelShouldReleaseConcurrentFlush() throws Exception {
        FutureWindow window = new FutureWindow(2, 5, TimeUnit.SECONDS);
        CompletableFuture<Void> pending = new CompletableFuture<>();
        window.add(pending);
        ExecutorService executor = register(Executors.newSingleThreadExecutor());
        Future<?> flush = executor.submit(window::flush);

        window.cancel();
        try {
            flush.get(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // An already active flush observes cancellation as a failure.
        }
        assertTrue(pending.isCancelled());
        assertTrue(window.isEmpty());
    }

    private <T extends ExecutorService> T register(T executor) {
        executors.add(executor);
        return executor;
    }
}
