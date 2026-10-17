package com.ifelio.ijob.contract.enums;

public enum TriggerStatus {
    WAITING,
    RUNNING,
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
}
