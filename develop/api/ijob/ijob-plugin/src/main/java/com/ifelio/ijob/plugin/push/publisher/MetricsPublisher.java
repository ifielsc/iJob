package com.ifelio.ijob.plugin.push.publisher;

import com.ifelio.ijob.plugin.push.PushMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class MetricsPublisher extends AbstractPushPublisher {
    private final PublishMetrics metrics;
    private final AtomicReference<Throwable> firstFailure = new AtomicReference<>();

    public MetricsPublisher(PushPublisher delegate, PublishMetrics metrics) {
        super(delegate);
        this.metrics = metrics;
    }

    @Override
    public CompletableFuture<Void> publish(PushMessage message) {
        metrics.begin();
        try {
            return track(delegate.publish(message));
        } catch (RuntimeException exception) {
            metrics.complete(exception);
            firstFailure.compareAndSet(null, exception);
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public CompletableFuture<Void> publishBatch(List<PushMessage> messages) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (PushMessage message : messages) {
            futures.add(publish(message));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Void> track(CompletableFuture<Void> future) {
        if (future == null) {
            IllegalStateException exception =
                    new IllegalStateException("push publisher returned null future");
            metrics.complete(exception);
            firstFailure.compareAndSet(null, exception);
            return CompletableFuture.failedFuture(exception);
        }
        CompletableFuture<Void> tracked = new CompletableFuture<>();
        future.whenComplete(
                (ignored, failure) -> {
                    metrics.complete(failure);
                    if (failure != null) {
                        firstFailure.compareAndSet(null, failure);
                    }
                    if (future.isCancelled()) {
                        tracked.cancel(false);
                    } else if (failure != null) {
                        tracked.completeExceptionally(failure);
                    } else {
                        tracked.complete(null);
                    }
                });
        tracked.whenComplete(
                (ignored, failure) -> {
                    if (tracked.isCancelled() && !future.isDone()) {
                        future.cancel(true);
                    }
                });
        return tracked;
    }

    @Override
    public void flush() {
        super.flush();
        // A full inner window may already have drained and failed during publish().
        // Retain that failure even when the caller relies on flush instead of joining batches.
        Throwable failure = firstFailure.get();
        if (failure != null) {
            throw new IllegalStateException("publish failed", failure);
        }
    }
}
