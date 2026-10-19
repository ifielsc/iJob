package com.ifielsc.ijob.infra.coordination.work;

public record SuspendedWorkPermit(Long triggerId, long fenceToken) {
    public SuspendedWorkPermit {
        if (triggerId == null || triggerId <= 0 || fenceToken < 0) {
            throw new IllegalArgumentException("Suspended work permit is invalid");
        }
    }
}
