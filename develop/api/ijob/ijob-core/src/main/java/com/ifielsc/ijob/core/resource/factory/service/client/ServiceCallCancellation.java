// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.service.client;

import okhttp3.Call;

import java.util.concurrent.CancellationException;

/** Invocation-owned cancellation; never closes a resource's shared HTTP client. */
public final class ServiceCallCancellation implements AutoCloseable {
    private boolean closed;
    private Call activeCall;
    private final long deadlineNanos;

    public ServiceCallCancellation() {
        this(0);
    }

    public ServiceCallCancellation(long deadlineNanos) {
        this.deadlineNanos = deadlineNanos;
    }

    public synchronized void check() {
        if (deadlineNanos != 0 && System.nanoTime() - deadlineNanos >= 0) {
            close();
            throw new CancellationException("Service invocation deadline exceeded");
        }
        if (closed || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Service invocation was cancelled");
        }
    }

    public synchronized void attach(Call call) {
        check();
        if (deadlineNanos != 0) call.timeout().deadlineNanoTime(deadlineNanos);
        if (closed || Thread.currentThread().isInterrupted()) {
            call.cancel();
            throw new CancellationException("Service invocation was cancelled");
        }
        activeCall = call;
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (activeCall != null) activeCall.cancel();
    }
}
