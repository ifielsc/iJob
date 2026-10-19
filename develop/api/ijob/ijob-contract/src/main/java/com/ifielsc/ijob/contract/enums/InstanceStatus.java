// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.enums;

public enum InstanceStatus {
    WAITING,
    RUNNING,
    PAUSED,
    WAITING_EVENT,
    SUCCESS,
    FAILED,
    STOPPED,
    TIMED_OUT,
    SKIPPED;

    public boolean isFinished() {
        return this == SUCCESS
                || this == FAILED
                || this == STOPPED
                || this == TIMED_OUT
                || this == SKIPPED;
    }

    public boolean isRetryable() {
        return this == FAILED || this == TIMED_OUT;
    }
}
