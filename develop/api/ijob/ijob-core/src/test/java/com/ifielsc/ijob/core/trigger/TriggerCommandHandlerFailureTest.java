// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.runtime.ApplicationRuntimeState;
import com.ifielsc.ijob.core.execute.ExecutionManager;
import com.ifielsc.ijob.infra.coordination.work.DatabaseTriggerWorkQueue;
import com.ifielsc.ijob.infra.coordination.work.ExecutionFenceCoordinator;
import com.ifielsc.ijob.infra.coordination.work.TriggerWorkClaim;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class TriggerCommandHandlerFailureTest {
    @Test
    void claimFailureMustReleaseReservationAndAllowTheSameTriggerAgain() {
        Fixture f = new Fixture(null);
        f.queue.failClaim = true;
        for (int i = 0; i < 3; i++) {
            assertThrows(
                    IllegalStateException.class, () -> f.handler.handle(new TriggerCommand(1L)));
        }
        assertEquals(3, f.queue.claimCalls);
    }

    @Test
    void everyPersistenceCompletionFailureMustReleaseLocalOwnership() {
        for (TriggerExecutionDisposition outcome :
                new TriggerExecutionDisposition[] {
                    TriggerExecutionDisposition.COMPLETED,
                    TriggerExecutionDisposition.SUSPENDED,
                    null
                }) {
            Fixture f = new Fixture(outcome);
            f.handler.handle(new TriggerCommand(1L));
            assertThrows(IllegalStateException.class, () -> f.tasks.removeFirst().run());
            assertNull(f.fences.activeClaim(1L));
            f.handler.handle(new TriggerCommand(1L));
            assertEquals(2, f.queue.claimCalls);
            assertEquals(1, f.tasks.size());
        }
    }

    private static final class Fixture {
        final List<Runnable> tasks = new ArrayList<>();
        final FailingQueue queue;
        final ExecutionFenceCoordinator fences;
        final TriggerCommandHandler handler;

        Fixture(TriggerExecutionDisposition outcome) {
            AppProperties properties = new AppProperties();
            properties.getRuntime().setMode(AppProperties.RuntimeMode.STANDALONE);
            properties.getRuntime().getWorkQueue().setMaxInflightRoots(1);
            ApplicationRuntimeState state = new ApplicationRuntimeState();
            state.markReady();
            queue = new FailingQueue(properties);
            fences = new ExecutionFenceCoordinator(properties);
            TriggerExecuteService service =
                    new TriggerExecuteService(null, null, null) {
                        @Override
                        public TriggerExecutionDisposition execute(Long id) {
                            return outcome;
                        }
                    };
            handler =
                    new TriggerCommandHandler(
                            tasks::add,
                            service,
                            null,
                            queue,
                            properties,
                            state,
                            fences,
                            new ExecutionManager(),
                            null,
                            Optional.empty());
        }
    }

    private static final class FailingQueue extends DatabaseTriggerWorkQueue {
        boolean failClaim;
        int claimCalls;

        FailingQueue(AppProperties properties) {
            super(null, properties);
        }

        @Override
        public TriggerWorkClaim claim(Long id, String owner) {
            claimCalls++;
            if (failClaim) throw new IllegalStateException("synthetic claim failure");
            return new TriggerWorkClaim(
                    id,
                    owner,
                    "claim-" + claimCalls,
                    claimCalls,
                    1,
                    3,
                    LocalDateTime.now().plusMinutes(1));
        }

        @Override
        public boolean complete(TriggerWorkClaim claim) {
            throw failure();
        }

        @Override
        public boolean suspend(TriggerWorkClaim claim) {
            throw failure();
        }

        @Override
        public boolean retry(TriggerWorkClaim claim, String error) {
            throw failure();
        }

        private IllegalStateException failure() {
            return new IllegalStateException("synthetic finish failure");
        }
    }
}
