// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@Slf4j
@RequiredArgsConstructor
public class JobRetryCommandDispatcher {
    private final JobRetryCommandHandler commandHandler;

    public void dispatchAfterCommit(JobRetryCommand command) {
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
                        dispatch(command);
                    }
                });
    }

    private void dispatch(JobRetryCommand command) {
        try {
            commandHandler.handle(command);
        } catch (RuntimeException exception) {
            log.error(
                    "dispatch job retry failed, instanceId={}",
                    command.instanceId(),
                    com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
        }
    }
}
