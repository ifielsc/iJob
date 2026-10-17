package com.ifelio.ijob.infra.coordination.work;

public enum TriggerWorkStatus {
    READY,
    CLAIMED,
    SUSPENDED,
    RETRY_WAIT,
    COMPLETED,
    DEAD
}
