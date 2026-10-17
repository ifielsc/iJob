package com.ifelio.ijob.core.notify;

import com.ifelio.ijob.contract.enums.NotifyType;

public record NotifyDispatchResult(
        NotifyDispatchStatus status,
        NotifyType type,
        NotifyAuditFailure failure,
        Integer httpStatus,
        long retryAfterMillis) {
    public NotifyDispatchResult(
            NotifyDispatchStatus status, NotifyType type, NotifyAuditFailure failure) {
        this(status, type, failure, null, 0);
    }

    public NotifyDispatchResult(NotifyDispatchStatus status, NotifyType type) {
        this(status, type, null);
    }
}
