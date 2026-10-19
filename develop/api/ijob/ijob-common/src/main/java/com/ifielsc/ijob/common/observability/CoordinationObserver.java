package com.ifielsc.ijob.common.observability;

import java.time.Duration;

/** Stable observation boundary for database coordination and recovery. */
public interface CoordinationObserver {
    CoordinationObserver NOOP = new CoordinationObserver() {};

    default void recordState(String component, String state, long value) {}

    default void recordLease(String lease, boolean active, long fenceToken) {}

    default void recordEvent(String component, String event) {}

    default void recordDelay(String component, Duration delay) {}

    default void recordPermitWaiting(long value) {}

    default void recordPermitWait(Duration duration, String outcome) {}
}
