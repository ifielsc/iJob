// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.apicall;

import com.ifielsc.ijob.common.exception.StopException;
import com.ifielsc.ijob.core.execute.TimeoutExceptionClassifier;
import com.ifielsc.ijob.core.execute.task.CancellationReason;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.resource.ResourceClientLease;
import com.ifielsc.ijob.core.resource.factory.service.ServiceRequest;
import com.ifielsc.ijob.core.resource.factory.service.ServiceResponse;
import com.ifielsc.ijob.core.resource.factory.service.client.ServiceClient;
import com.ifielsc.ijob.core.resource.factory.service.client.ServiceInvocation;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** One attempt owns the original resource lease until both local execution and stop finish. */
@Slf4j
final class ApiCallAttempt implements AutoCloseable {
    private final ResourceClientLease<ServiceClient> lease;
    private final ServiceRequest stopRequest;
    private final int stopSeconds;
    private final TaskContext context;
    private final ApiRemoteStopDispatcher dispatcher;
    private final AtomicInteger owners = new AtomicInteger(1);
    private final AutoCloseable subscription;
    private ServiceInvocation invocation;
    private boolean sent, completed, cancelled, stopScheduled;

    ApiCallAttempt(
            ResourceClientLease<ServiceClient> lease,
            ServiceRequest stopRequest,
            int stopSeconds,
            TaskContext context,
            ApiRemoteStopDispatcher dispatcher) {
        this.lease = lease;
        this.stopRequest = stopRequest;
        this.stopSeconds = stopSeconds;
        this.context = context;
        this.dispatcher = dispatcher;
        subscription = context.onCancellation(this::cancel);
    }

    ServiceResponse invoke(ServiceRequest request) {
        try (ServiceInvocation call = lease.getClient().newInvocation(request)) {
            synchronized (this) {
                invocation = call;
                if (cancelled) {
                    call.close();
                    throw new StopException();
                }
            }
            context.registerResource(call);
            context.checkExecution();
            ServiceResponse response =
                    call.executeWithDispatch(
                            () -> {
                                synchronized (this) {
                                    if (cancelled) throw new StopException();
                                    sent = true;
                                }
                            });
            synchronized (this) {
                completed = true;
            }
            return response;
        } catch (RuntimeException failure) {
            if (TimeoutExceptionClassifier.isTimeout(failure)) cancel(CancellationReason.TIMEOUT);
            throw failure;
        } finally {
            synchronized (this) {
                completed = true;
                invocation = null;
            }
        }
    }

    private void cancel(CancellationReason reason) {
        ServiceInvocation local;
        boolean notify;
        synchronized (this) {
            if (completed || cancelled) return;
            cancelled = true;
            local = invocation;
            notify =
                    sent
                            && stopRequest != null
                            && reason != CancellationReason.FENCED
                            && !stopScheduled;
            if (notify) {
                stopScheduled = true;
                owners.incrementAndGet();
                stopRequest.setDeadlineNanos(
                        System.nanoTime() + TimeUnit.SECONDS.toNanos(stopSeconds));
            }
        }
        try {
            if (local != null) local.close();
        } finally {
            if (notify && !dispatcher.submit(this::sendStop, this::release)) observe("REJECTED");
        }
    }

    private void sendStop() {
        if (fenced()) {
            observe("FENCED");
            return;
        }
        if (expired()) {
            observe("TIMED_OUT");
            return;
        }
        try (ServiceInvocation call = lease.getClient().newInvocation(stopRequest);
                AutoCloseable registration = dispatcher.register(call)) {
            if (expired()) {
                observe("TIMED_OUT");
                return;
            }
            ServiceResponse response =
                    call.executeWithDispatch(
                            () -> {
                                if (fenced()) throw new StopException();
                            });
            observe(
                    expired()
                            ? "TIMED_OUT"
                            : lease.getClient().success(response) ? "ACCEPTED" : "FAILED");
        } catch (Exception failure) {
            observe(
                    fenced()
                            ? "FENCED"
                            : expired() || TimeoutExceptionClassifier.isTimeout(failure)
                                    ? "TIMED_OUT"
                                    : "FAILED");
            log.warn(
                    "Remote stop failed, instanceId={}",
                    context.getInstanceId(),
                    com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(failure));
        }
    }

    private boolean fenced() {
        return context.getHandle().getCancellationReason() == CancellationReason.FENCED;
    }

    private boolean expired() {
        return System.nanoTime() - stopRequest.getDeadlineNanos() >= 0;
    }

    private void observe(String outcome) {
        // Observation must never turn a completed business request into a retryable failure.
        try {
            context.logInfo(
                    "API remote stop outcome=" + outcome + "; remote completion is not confirmed");
        } catch (RuntimeException failure) {
            log.warn("Remote stop observation unavailable, instanceId={}", context.getInstanceId());
        }
    }

    private void release() {
        if (owners.decrementAndGet() == 0) lease.close();
    }

    @Override
    public void close() {
        try {
            subscription.close();
        } catch (Exception failure) {
            log.warn(
                    "Remote stop subscription cleanup failed, instanceId={}",
                    context.getInstanceId());
        } finally {
            release();
        }
    }
}
