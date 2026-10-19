// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.trigger;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executor;

@Component
@Slf4j
public class TriggerCommandDispatcher {
    private final TriggerCommandHandler triggerCommandHandler;
    private final Executor executor;

    @Autowired
    public TriggerCommandDispatcher(
            TriggerCommandHandler triggerCommandHandler,
            @Qualifier("triggerExecutor") Executor executor) {
        this.triggerCommandHandler = triggerCommandHandler;
        this.executor = executor;
    }

    protected TriggerCommandDispatcher(TriggerCommandHandler triggerCommandHandler) {
        this(triggerCommandHandler, Runnable::run);
    }

    public void dispatchAfterCommit(Long triggerId) {
        TriggerCommand command = new TriggerCommand(triggerId);
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            dispatch(command);
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Active transaction does not support synchronization");
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        // The submitting connection remains bound until callback cleanup.
                        // Claim on an executor thread so ownership commits before execution.
                        try {
                            executor.execute(() -> dispatch(command));
                        } catch (RuntimeException exception) {
                            // Persistent work remains queued for the normal scanner.
                            log.error(
                                    "dispatch trigger command scheduling failed, triggerId={}",
                                    command.getTriggerId(),
                                    com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(
                                            exception));
                        }
                    }
                });
    }

    private void dispatch(TriggerCommand command) {
        try {
            triggerCommandHandler.handle(command);
        } catch (RuntimeException exception) {
            log.error(
                    "dispatch trigger command failed, triggerId={}",
                    command.getTriggerId(),
                    com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
        }
    }
}
