package com.ifielsc.ijob.core.execute.task;

/** Why the active attempt is being cancelled; ordinary resource release is not cancellation. */
public enum CancellationReason {
    STOP,
    TIMEOUT,
    FENCED
}
