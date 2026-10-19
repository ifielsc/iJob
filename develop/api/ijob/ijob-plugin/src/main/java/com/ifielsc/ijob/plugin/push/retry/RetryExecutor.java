// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.push.retry;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class RetryExecutor {
    public CompletableFuture<Void> execute(
            Supplier<CompletableFuture<Void>> action, RetryPolicy policy) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        AtomicReference<CompletableFuture<?>> current = new AtomicReference<>();
        result.whenComplete(
                (ignored, failure) -> {
                    if (result.isCancelled()) {
                        CompletableFuture<?> future = current.get();
                        if (future != null) {
                            future.cancel(true);
                        }
                    }
                });
        doRetry(action, policy, 0, result, current);
        return result;
    }

    private void doRetry(
            Supplier<CompletableFuture<Void>> action,
            RetryPolicy policy,
            int retryCount,
            CompletableFuture<Void> result,
            AtomicReference<CompletableFuture<?>> current) {
        if (result.isDone()) {
            return;
        }
        CompletableFuture<Void> attempt;
        try {
            attempt = action.get();
            if (attempt == null) {
                throw new IllegalStateException("retry action returned null future");
            }
        } catch (RuntimeException e) {
            retryOrFail(action, policy, retryCount, result, current, e);
            return;
        }
        current.set(attempt);
        if (result.isDone()) {
            attempt.cancel(true);
            return;
        }
        attempt.whenComplete(
                (ignored, failure) -> {
                    if (result.isDone()) {
                        return;
                    }
                    if (failure == null) {
                        result.complete(null);
                    } else {
                        retryOrFail(action, policy, retryCount, result, current, failure);
                    }
                });
    }

    private void retryOrFail(
            Supplier<CompletableFuture<Void>> action,
            RetryPolicy policy,
            int retryCount,
            CompletableFuture<Void> result,
            AtomicReference<CompletableFuture<?>> current,
            Throwable failure) {
        if (result.isDone()) {
            return;
        }
        if (retryCount >= policy.getMaxRetries()) {
            result.completeExceptionally(failure);
            return;
        }
        long delay = calculateDelay(policy, retryCount);
        CompletableFuture<Void> scheduled =
                CompletableFuture.runAsync(
                        () -> doRetry(action, policy, retryCount + 1, result, current),
                        CompletableFuture.delayedExecutor(
                                delay, java.util.concurrent.TimeUnit.MILLISECONDS));
        current.set(scheduled);
        if (result.isDone()) {
            scheduled.cancel(true);
        }
    }

    private long calculateDelay(RetryPolicy policy, int retryCount) {
        if (!policy.isExponential()) {
            return policy.getInitialInterval();
        }

        if (retryCount >= Long.SIZE - 1) {
            return Long.MAX_VALUE;
        }
        long multiplier = 1L << retryCount;
        long initial = policy.getInitialInterval();
        return initial > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : initial * multiplier;
    }
}
