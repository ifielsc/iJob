// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.state;

import com.ifielsc.ijob.contract.enums.TriggerStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Defines legal persisted trigger transitions. A trigger is claimed exactly once and never returns
 * to WAITING. Capacity rejection may fail a waiting trigger before it is claimed.
 */
public final class TriggerStateMachine {
    private static final Map<TriggerStatus, Set<TriggerStatus>> TRANSITIONS = transitions();

    private TriggerStateMachine() {}

    public static boolean canTransition(TriggerStatus current, TriggerStatus next) {
        if (current == null || next == null || current == next) {
            return false;
        }
        return TRANSITIONS.getOrDefault(current, Set.of()).contains(next);
    }

    private static Map<TriggerStatus, Set<TriggerStatus>> transitions() {
        Map<TriggerStatus, Set<TriggerStatus>> result = new EnumMap<>(TriggerStatus.class);
        result.put(
                TriggerStatus.WAITING,
                EnumSet.of(TriggerStatus.RUNNING, TriggerStatus.FAILED, TriggerStatus.SKIPPED));
        result.put(
                TriggerStatus.RUNNING,
                EnumSet.of(
                        TriggerStatus.SUCCESS,
                        TriggerStatus.FAILED,
                        TriggerStatus.STOPPED,
                        TriggerStatus.TIMED_OUT,
                        TriggerStatus.SKIPPED));
        return Map.copyOf(result);
    }
}
