// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ifielsc.ijob.contract.enums.ExecuteCondition;
import com.ifielsc.ijob.contract.enums.FailStrategy;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.core.execute.graph.JobExecutionNode;
import com.ifielsc.ijob.core.execute.graph.JobExecutionPlan;
import com.ifielsc.ijob.core.job.definition.JobExecutionSpec;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Set;

class JobRetryCommandDispatcherTest {
    @Test
    void shouldDispatchImmediatelyWithoutTransaction() {
        RecordingHandler handler = new RecordingHandler();
        JobRetryCommandDispatcher dispatcher = new JobRetryCommandDispatcher(handler);
        JobRetryCommand command = command(17L);

        dispatcher.dispatchAfterCommit(command);

        assertEquals(command, handler.command);
    }

    @Test
    void shouldDispatchOnlyAfterTransactionCommit() {
        RecordingHandler handler = new RecordingHandler();
        JobRetryCommandDispatcher dispatcher = new JobRetryCommandDispatcher(handler);
        JobRetryCommand command = command(18L);

        beginSynchronization();
        try {
            dispatcher.dispatchAfterCommit(command);
            assertNull(handler.command);

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());
            synchronizations.getFirst().afterCommit();
            assertEquals(command, handler.command);
        } finally {
            endSynchronization();
        }
    }

    @Test
    void shouldNotDispatchWhenTransactionRollsBack() {
        RecordingHandler handler = new RecordingHandler();
        JobRetryCommandDispatcher dispatcher = new JobRetryCommandDispatcher(handler);

        beginSynchronization();
        try {
            dispatcher.dispatchAfterCommit(command(19L));
            TransactionSynchronization synchronization =
                    TransactionSynchronizationManager.getSynchronizations().getFirst();
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            assertNull(handler.command);
        } finally {
            endSynchronization();
        }
    }

    private static JobRetryCommand command(Long instanceId) {
        JobExecutionNode node =
                new JobExecutionNode(
                        61L,
                        70L,
                        "node-61",
                        "Node 61",
                        TargetType.TASK,
                        161L,
                        ExecuteCondition.ALL_SUCCESS,
                        FailStrategy.STOP);
        JobExecutionPlan plan =
                new JobExecutionPlan(
                        70L,
                        List.of(node),
                        Map.of(61L, node),
                        Map.of(61L, List.of()),
                        Map.of(61L, List.of()),
                        Map.of(61L, 0),
                        List.of(node));
        JobExecutionSpec spec = new JobExecutionSpec(7L, 70L, 3L, "job", 60);
        return new JobRetryCommand(instanceId, spec, plan, Set.of(61L));
    }

    private static void beginSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    private static void endSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private static final class RecordingHandler extends JobRetryCommandHandler {
        private JobRetryCommand command;

        private RecordingHandler() {
            super(null, null, null);
        }

        @Override
        public void handle(JobRetryCommand selectedCommand) {
            command = selectedCommand;
        }
    }
}
