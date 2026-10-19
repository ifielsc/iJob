// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.common.runtime;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ApplicationRuntimeState {
    private final AtomicReference<RuntimePhase> phase =
            new AtomicReference<>(RuntimePhase.STARTING);
    private final AtomicReference<Instant> changedAt = new AtomicReference<>(Instant.now());
    private final AtomicReference<String> fenceReason = new AtomicReference<>();

    public RuntimePhase phase() {
        return phase.get();
    }

    public Instant changedAt() {
        return changedAt.get();
    }

    public boolean isReady() {
        return phase() == RuntimePhase.READY;
    }

    public String fenceReason() {
        return fenceReason.get();
    }

    public void markReady() {
        transitionUnlessFenced(RuntimePhase.READY);
    }

    public void markDraining() {
        transitionUnlessFenced(RuntimePhase.DRAINING);
    }

    public boolean markFenced(String reason) {
        while (true) {
            RuntimePhase current = phase.get();
            if (current == RuntimePhase.FENCED) {
                return false;
            }
            if (phase.compareAndSet(current, RuntimePhase.FENCED)) {
                fenceReason.compareAndSet(null, normalizeReason(reason));
                changedAt.set(Instant.now());
                return true;
            }
        }
    }

    private void transitionUnlessFenced(RuntimePhase next) {
        while (true) {
            RuntimePhase current = phase.get();
            if (current == RuntimePhase.FENCED || current == next) {
                return;
            }
            if (phase.compareAndSet(current, next)) {
                changedAt.set(Instant.now());
                return;
            }
        }
    }

    private String normalizeReason(String reason) {
        return reason == null || reason.isBlank()
                ? "Runtime ownership could not be proven"
                : reason;
    }
}
