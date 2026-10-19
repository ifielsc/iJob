// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.state;

import com.ifielsc.ijob.contract.enums.InstanceStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Defines legal persisted state transitions for root and node instances. Job nodes use the same
 * lifecycle because every node is persisted as an {@code ifj_instance} child row.
 */
public final class InstanceStateMachine {
    private static final Map<InstanceStatus, Set<InstanceStatus>> TRANSITIONS = transitions();

    private InstanceStateMachine() {}

    public static boolean canTransition(InstanceStatus current, InstanceStatus next) {
        if (current == null || next == null || current == next) {
            return false;
        }
        return TRANSITIONS.getOrDefault(current, Set.of()).contains(next);
    }

    public static boolean canRetry(InstanceStatus current) {
        return current != null && current.isRetryable();
    }

    public static boolean canResetJobBranch(InstanceStatus current) {
        return current != null && current.isFinished();
    }

    private static Map<InstanceStatus, Set<InstanceStatus>> transitions() {
        Map<InstanceStatus, Set<InstanceStatus>> result = new EnumMap<>(InstanceStatus.class);
        result.put(
                InstanceStatus.WAITING,
                EnumSet.of(
                        InstanceStatus.RUNNING,
                        InstanceStatus.FAILED,
                        InstanceStatus.STOPPED,
                        InstanceStatus.TIMED_OUT,
                        InstanceStatus.SKIPPED));
        result.put(
                InstanceStatus.RUNNING,
                EnumSet.of(
                        InstanceStatus.PAUSED,
                        InstanceStatus.WAITING_EVENT,
                        InstanceStatus.SUCCESS,
                        InstanceStatus.SKIPPED,
                        InstanceStatus.FAILED,
                        InstanceStatus.STOPPED,
                        InstanceStatus.TIMED_OUT));
        result.put(
                InstanceStatus.PAUSED,
                EnumSet.of(
                        InstanceStatus.RUNNING,
                        InstanceStatus.SUCCESS,
                        InstanceStatus.SKIPPED,
                        InstanceStatus.FAILED,
                        InstanceStatus.STOPPED,
                        InstanceStatus.TIMED_OUT));
        result.put(
                InstanceStatus.WAITING_EVENT,
                EnumSet.of(
                        InstanceStatus.WAITING,
                        InstanceStatus.SUCCESS,
                        InstanceStatus.FAILED,
                        InstanceStatus.STOPPED,
                        InstanceStatus.TIMED_OUT));
        return Map.copyOf(result);
    }
}
