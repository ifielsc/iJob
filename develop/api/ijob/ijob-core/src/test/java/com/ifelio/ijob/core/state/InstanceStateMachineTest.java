package com.ifelio.ijob.core.state;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.contract.enums.InstanceStatus;

import org.junit.jupiter.api.Test;

class InstanceStateMachineTest {
    @Test
    void shouldAllowOnlyDeclaredRuntimeTransitions() {
        assertTrue(
                InstanceStateMachine.canTransition(InstanceStatus.WAITING, InstanceStatus.RUNNING));
        assertTrue(
                InstanceStateMachine.canTransition(InstanceStatus.WAITING, InstanceStatus.SKIPPED));
        assertTrue(
                InstanceStateMachine.canTransition(
                        InstanceStatus.WAITING, InstanceStatus.TIMED_OUT));
        assertTrue(
                InstanceStateMachine.canTransition(InstanceStatus.RUNNING, InstanceStatus.PAUSED));
        assertTrue(
                InstanceStateMachine.canTransition(
                        InstanceStatus.RUNNING, InstanceStatus.WAITING_EVENT));
        assertTrue(
                InstanceStateMachine.canTransition(
                        InstanceStatus.WAITING_EVENT, InstanceStatus.WAITING));
        assertTrue(
                InstanceStateMachine.canTransition(
                        InstanceStatus.WAITING_EVENT, InstanceStatus.SUCCESS));
        assertTrue(
                InstanceStateMachine.canTransition(
                        InstanceStatus.WAITING_EVENT, InstanceStatus.FAILED));
        assertTrue(
                InstanceStateMachine.canTransition(
                        InstanceStatus.WAITING_EVENT, InstanceStatus.TIMED_OUT));
        assertTrue(
                InstanceStateMachine.canTransition(
                        InstanceStatus.WAITING_EVENT, InstanceStatus.STOPPED));
        assertTrue(
                InstanceStateMachine.canTransition(
                        InstanceStatus.PAUSED, InstanceStatus.TIMED_OUT));

        assertFalse(
                InstanceStateMachine.canTransition(InstanceStatus.SUCCESS, InstanceStatus.RUNNING));
        assertFalse(
                InstanceStateMachine.canTransition(InstanceStatus.WAITING, InstanceStatus.SUCCESS));
        assertTrue(
                InstanceStateMachine.canTransition(InstanceStatus.RUNNING, InstanceStatus.SKIPPED));
        assertFalse(
                InstanceStateMachine.canTransition(
                        InstanceStatus.WAITING_EVENT, InstanceStatus.PAUSED));
    }

    @Test
    void shouldPersistPluginSkipWithoutReopeningTerminalInstances() {
        // A runner may return SKIPPED after entering RUNNING or racing with pause.
        assertTrue(
                InstanceStateMachine.canTransition(InstanceStatus.PAUSED, InstanceStatus.SKIPPED));
        assertFalse(InstanceStateMachine.canRetry(InstanceStatus.SKIPPED));
        for (InstanceStatus next : InstanceStatus.values()) {
            assertFalse(InstanceStateMachine.canTransition(InstanceStatus.SKIPPED, next));
        }
    }

    @Test
    void shouldSeparateRootRetryFromJobBranchReset() {
        assertTrue(InstanceStateMachine.canRetry(InstanceStatus.FAILED));
        assertTrue(InstanceStateMachine.canRetry(InstanceStatus.TIMED_OUT));
        assertFalse(InstanceStateMachine.canRetry(InstanceStatus.STOPPED));
        assertFalse(InstanceStateMachine.canRetry(InstanceStatus.SUCCESS));

        assertTrue(InstanceStateMachine.canResetJobBranch(InstanceStatus.SUCCESS));
        assertTrue(InstanceStateMachine.canResetJobBranch(InstanceStatus.STOPPED));
        assertFalse(InstanceStateMachine.canResetJobBranch(InstanceStatus.RUNNING));
    }
}
