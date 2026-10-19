// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.concurrency;

/** Raised when a task cannot acquire all required concurrency permits before its deadline. */
public class PermitWaitTimeoutException extends RuntimeException {
    public PermitWaitTimeoutException() {
        super("Concurrency permit wait timed out");
    }
}
