// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute;

import com.ifielsc.ijob.common.diagnostic.SafeDiagnostic;
import com.ifielsc.ijob.core.error.CoreMessage;
import com.ifielsc.ijob.core.execute.task.TaskResult;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Component
@Slf4j
public class JobRetryCommandHandler {
    private final Executor executor;
    private final JobExecuteService jobExecuteService;
    private final InstanceRuntimeService instanceRuntimeService;

    public JobRetryCommandHandler(
            @Qualifier("triggerExecutor") Executor executor,
            JobExecuteService jobExecuteService,
            InstanceRuntimeService instanceRuntimeService) {
        this.executor = executor;
        this.jobExecuteService = jobExecuteService;
        this.instanceRuntimeService = instanceRuntimeService;
    }

    public void handle(JobRetryCommand command) {
        try {
            executor.execute(
                    () -> {
                        try {
                            jobExecuteService.executeRetry(
                                    command.instanceId(),
                                    command.spec(),
                                    command.plan(),
                                    command.retryNodeIds());
                        } catch (Exception exception) {
                            instanceRuntimeService.completeTask(
                                    command.instanceId(),
                                    TaskResult.fail(CoreMessage.JOB_RETRY_FAILED));
                            log.error(
                                    "execute job retry failed, instanceId={}",
                                    command.instanceId(),
                                    SafeDiagnostic.sanitized(exception));
                        }
                    });
        } catch (RejectedExecutionException exception) {
            instanceRuntimeService.completeTask(
                    command.instanceId(), TaskResult.fail(CoreMessage.JOB_RETRY_CAPACITY));
            throw exception;
        }
    }
}
