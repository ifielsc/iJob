// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify;

import com.ifielsc.ijob.contract.enums.NotifyType;

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
