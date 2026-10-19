// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.coordination.work;

public record ExecutionFencePermit(Mode mode, String claimToken, Long fenceToken) {
    public enum Mode {
        UNFENCED,
        CLAIMED,
        SUSPENDED
    }

    public static ExecutionFencePermit unfenced() {
        return new ExecutionFencePermit(Mode.UNFENCED, null, null);
    }

    public static ExecutionFencePermit claimed(TriggerWorkClaim claim) {
        return new ExecutionFencePermit(Mode.CLAIMED, claim.claimToken(), claim.fenceToken());
    }

    public static ExecutionFencePermit suspended(SuspendedWorkPermit permit) {
        return new ExecutionFencePermit(Mode.SUSPENDED, null, permit.fenceToken());
    }

    public boolean isClaimed() {
        return mode == Mode.CLAIMED;
    }

    public boolean isSuspended() {
        return mode == Mode.SUSPENDED;
    }
}
