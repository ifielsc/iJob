package com.ifielsc.ijob.core.notify.channel;

import com.ifielsc.ijob.core.notify.NotifyAuditFailure;

/** Stable delivery classification; raw causes may only be logged through SafeDiagnostic. */
public final class NotifyDeliveryException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final NotifyAuditFailure failure;
    private Integer httpStatus;
    private long retryAfterMillis;
    private boolean global;

    public NotifyDeliveryException(
            NotifyAuditFailure failure, Integer httpStatus, long retryAfterMillis, boolean global) {
        super(failure.getSummary());
        this.failure = failure;
        this.httpStatus = httpStatus;
        this.retryAfterMillis = retryAfterMillis;
        this.global = global;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public long retryAfterMillis() {
        return retryAfterMillis;
    }

    public boolean global() {
        return global;
    }

    public NotifyDeliveryException(NotifyAuditFailure failure, Throwable cause) {
        super(failure.getSummary(), cause);
        this.failure = failure;
    }

    public NotifyAuditFailure failure() {
        return failure;
    }
}
