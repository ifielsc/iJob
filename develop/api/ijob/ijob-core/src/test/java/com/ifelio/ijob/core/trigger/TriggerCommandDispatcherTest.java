package com.ifelio.ijob.core.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

class TriggerCommandDispatcherTest {
    @Test
    void shouldDispatchImmediatelyWithoutTransaction() {
        RecordingHandler handler = new RecordingHandler();
        TriggerCommandDispatcher dispatcher = new TriggerCommandDispatcher(handler);

        dispatcher.dispatchAfterCommit(17L);

        assertEquals(17L, handler.triggerId);
    }

    @Test
    void shouldDispatchOnlyAfterTransactionCommit() {
        RecordingHandler handler = new RecordingHandler();
        List<Runnable> scheduled = new ArrayList<>();
        TriggerCommandDispatcher dispatcher = new TriggerCommandDispatcher(handler, scheduled::add);

        beginSynchronization();
        try {
            dispatcher.dispatchAfterCommit(18L);
            assertNull(handler.triggerId);

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());
            synchronizations.getFirst().afterCommit();
            assertNull(handler.triggerId);
            assertEquals(1, scheduled.size());
        } finally {
            endSynchronization();
        }
        scheduled.getFirst().run();
        assertEquals(18L, handler.triggerId);
    }

    @Test
    void shouldNotDispatchWhenTransactionRollsBack() {
        RecordingHandler handler = new RecordingHandler();
        TriggerCommandDispatcher dispatcher = new TriggerCommandDispatcher(handler);

        beginSynchronization();
        try {
            dispatcher.dispatchAfterCommit(19L);
            TransactionSynchronization synchronization =
                    TransactionSynchronizationManager.getSynchronizations().getFirst();
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            assertNull(handler.triggerId);
        } finally {
            endSynchronization();
        }
    }

    private static void beginSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    private static void endSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private static final class RecordingHandler extends TriggerCommandHandler {
        private Long triggerId;

        private RecordingHandler() {
            super(null, null, null);
        }

        @Override
        public void handle(TriggerCommand command) {
            triggerId = command.getTriggerId();
        }
    }
}
