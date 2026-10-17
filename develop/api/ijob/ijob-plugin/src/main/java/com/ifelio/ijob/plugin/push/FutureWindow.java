package com.ifelio.ijob.plugin.push;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/** Tracks pending publications and flushes when the pending batch reaches its limit. */
public class FutureWindow {
    private final int maxInflight;

    private final long timeout;

    private final TimeUnit timeoutUnit;

    private final List<CompletableFuture<Void>> pending = new ArrayList<>();

    private final Set<CompletableFuture<Void>> active = new LinkedHashSet<>();
    private final Object stateLock = new Object();
    private final Object flushLock = new Object();
    private boolean closed;

    public FutureWindow(int maxInflight, long timeout, TimeUnit timeoutUnit) {
        if (maxInflight <= 0) {
            throw new IllegalArgumentException("maxInflight must > 0");
        }
        if (timeout <= 0 || timeoutUnit == null) {
            throw new IllegalArgumentException("timeout must > 0 and timeoutUnit is required");
        }

        this.maxInflight = maxInflight;
        this.timeout = timeout;
        this.timeoutUnit = timeoutUnit;
    }

    public void add(CompletableFuture<Void> future) {
        Objects.requireNonNull(future, "future");
        boolean full;
        boolean reject;
        synchronized (stateLock) {
            reject = closed;
            if (!reject) {
                pending.add(future);
            }
            full = pending.size() >= maxInflight;
        }
        if (reject) {
            future.cancel(true);
            return;
        }
        if (full) {
            flush();
        }
    }

    /**
     * Waits for a snapshot of pending publications; later additions remain pending for another
     * flush.
     */
    public void flush() {
        synchronized (flushLock) {
            List<CompletableFuture<Void>> snapshot;
            synchronized (stateLock) {
                if (pending.isEmpty()) {
                    return;
                }
                snapshot = List.copyOf(pending);
                pending.clear();
                active.addAll(snapshot);
            }
            CompletableFuture<Void> all = allOfFailFast(snapshot);
            try {
                all.get(timeout, timeoutUnit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancel(snapshot);
                throw new IllegalStateException("publish interrupted", e);
            } catch (ExecutionException e) {
                cancel(snapshot);
                throw new IllegalStateException("publish failed", e.getCause());
            } catch (TimeoutException e) {
                cancel(snapshot);
                throw new IllegalStateException("publish timeout", e);
            } finally {
                synchronized (stateLock) {
                    active.removeAll(snapshot);
                }
            }
        }
    }

    /** Counts both pending publications and the active flush snapshot. */
    public int size() {
        synchronized (stateLock) {
            return pending.size() + active.size();
        }
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    /** Cancels tracked publications without permanently closing the window. */
    public void clear() {
        cancel();
    }

    public void cancel() {
        cancel(false);
    }

    public void close() {
        cancel(true);
    }

    private void cancel(boolean close) {
        List<CompletableFuture<Void>> snapshot;
        synchronized (stateLock) {
            closed = closed || close;
            snapshot = new ArrayList<>(pending.size() + active.size());
            snapshot.addAll(pending);
            snapshot.addAll(active);
            pending.clear();
            active.clear();
        }
        cancel(snapshot);
    }

    private void cancel(List<CompletableFuture<Void>> snapshot) {
        for (CompletableFuture<Void> future : snapshot) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }

    private CompletableFuture<Void> allOfFailFast(List<CompletableFuture<Void>> snapshot) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        AtomicInteger remaining = new AtomicInteger(snapshot.size());
        for (CompletableFuture<Void> future : snapshot) {
            future.whenComplete(
                    (ignored, failure) -> {
                        if (failure != null) {
                            result.completeExceptionally(failure);
                        } else if (remaining.decrementAndGet() == 0) {
                            result.complete(null);
                        }
                    });
        }
        return result;
    }
}
