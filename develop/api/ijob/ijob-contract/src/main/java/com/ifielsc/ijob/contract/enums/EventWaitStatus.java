// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.enums;

public enum EventWaitStatus {
    WAITING,
    MATCHED,
    APPROVED,
    REJECTED,
    TIMED_OUT,
    WITHDRAWN,
    STOPPED;

    public boolean isFinished() {
        return this != WAITING;
    }
}
