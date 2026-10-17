package com.ifelio.ijob.contract.enums;

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
