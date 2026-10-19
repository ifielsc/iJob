package com.ifielsc.ijob.common.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApplicationRuntimeStateTest {
    @Test
    void fencedStateShouldBeIrreversibleAndKeepOriginalReason() {
        ApplicationRuntimeState state = new ApplicationRuntimeState();
        state.markReady();

        assertTrue(state.markFenced("standalone lock lost"));
        state.markReady();
        state.markDraining();

        assertEquals(RuntimePhase.FENCED, state.phase());
        assertEquals("standalone lock lost", state.fenceReason());
        assertFalse(state.markFenced("replacement reason"));
        assertEquals("standalone lock lost", state.fenceReason());
    }
}
