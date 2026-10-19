package com.ifielsc.ijob.plugin.push.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class RetryExecutorTest {
    private final RetryExecutor executor = new RetryExecutor();

    @Test
    void asynchronousFailureShouldRetryUntilSuccess() {
        AtomicInteger attempts = new AtomicInteger();

        executor.execute(
                        () ->
                                attempts.incrementAndGet() < 3
                                        ? CompletableFuture.failedFuture(
                                                new IllegalStateException("temporary"))
                                        : CompletableFuture.completedFuture(null),
                        policy(3, 0))
                .join();

        assertEquals(3, attempts.get());
    }

    @Test
    void synchronousFailureShouldAlsoRetry() {
        AtomicInteger attempts = new AtomicInteger();

        executor.execute(
                        () -> {
                            if (attempts.incrementAndGet() == 1) {
                                throw new IllegalStateException("temporary");
                            }
                            return CompletableFuture.completedFuture(null);
                        },
                        policy(1, 0))
                .join();

        assertEquals(2, attempts.get());
    }

    @Test
    void exhaustedRetriesShouldRetainFailure() {
        AtomicInteger attempts = new AtomicInteger();
        CompletableFuture<Void> result =
                executor.execute(
                        () -> {
                            attempts.incrementAndGet();
                            return CompletableFuture.failedFuture(
                                    new IllegalArgumentException("rejected"));
                        },
                        policy(2, 0));

        assertThrows(Exception.class, result::join);
        assertEquals(3, attempts.get());
    }

    @Test
    void cancellationShouldCancelAttemptAndSuppressDelayedRetry() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch unexpectedRetry = new CountDownLatch(1);
        CompletableFuture<Void> attempt = new CompletableFuture<>();
        CompletableFuture<Void> result =
                executor.execute(
                        () -> {
                            if (attempts.incrementAndGet() > 1) {
                                unexpectedRetry.countDown();
                            }
                            return attempt;
                        },
                        policy(3, 100));

        result.cancel(true);

        assertTrue(attempt.isCancelled());
        assertTrue(result.isCancelled());
        assertFalse(unexpectedRetry.await(200, TimeUnit.MILLISECONDS));
        assertEquals(1, attempts.get());
    }

    @Test
    void cancellationDuringBackoffShouldSuppressNextAttempt() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch unexpectedRetry = new CountDownLatch(1);
        CompletableFuture<Void> result =
                executor.execute(
                        () -> {
                            if (attempts.incrementAndGet() > 1) {
                                unexpectedRetry.countDown();
                            }
                            return CompletableFuture.failedFuture(
                                    new IllegalStateException("temporary"));
                        },
                        policy(3, 200));

        result.cancel(true);

        assertTrue(result.isCancelled());
        assertFalse(unexpectedRetry.await(300, TimeUnit.MILLISECONDS));
        assertEquals(1, attempts.get());
    }

    private RetryPolicy policy(int retries, long interval) {
        return RetryPolicy.builder()
                .maxRetries(retries)
                .initialInterval(interval)
                .exponential(false)
                .build();
    }
}
