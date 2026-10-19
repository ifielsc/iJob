package com.ifielsc.ijob.core.state;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.contract.enums.TriggerStatus;

import org.junit.jupiter.api.Test;

class TriggerStateMachineTest {
    @Test
    void shouldClaimOnceAndKeepTerminalStatesImmutable() {
        assertTrue(TriggerStateMachine.canTransition(TriggerStatus.WAITING, TriggerStatus.RUNNING));
        assertTrue(TriggerStateMachine.canTransition(TriggerStatus.WAITING, TriggerStatus.SKIPPED));
        assertTrue(TriggerStateMachine.canTransition(TriggerStatus.WAITING, TriggerStatus.FAILED));
        assertTrue(TriggerStateMachine.canTransition(TriggerStatus.RUNNING, TriggerStatus.SUCCESS));
        assertTrue(
                TriggerStateMachine.canTransition(TriggerStatus.RUNNING, TriggerStatus.TIMED_OUT));
        assertTrue(TriggerStateMachine.canTransition(TriggerStatus.RUNNING, TriggerStatus.SKIPPED));

        assertFalse(
                TriggerStateMachine.canTransition(TriggerStatus.RUNNING, TriggerStatus.WAITING));
        assertFalse(TriggerStateMachine.canTransition(TriggerStatus.FAILED, TriggerStatus.RUNNING));
        assertFalse(TriggerStateMachine.canTransition(TriggerStatus.SUCCESS, TriggerStatus.FAILED));
    }
}
