package com.ifelio.ijob.contract.enums;

public enum RuntimeCommandStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    REJECTED,
    FAILED,
    EXPIRED;

    public boolean isFinished() {
        return this == SUCCEEDED || this == REJECTED || this == FAILED || this == EXPIRED;
    }
}
