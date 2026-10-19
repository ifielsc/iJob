// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.batchetl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.exception.StopException;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.core.execute.ExecutionHandle;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;
import com.ifielsc.ijob.plugin.batchetl.config.BatchEtlTaskConfig;
import com.ifielsc.ijob.plugin.batchetl.config.BatchEtlTaskConfigValidator;
import com.ifielsc.ijob.plugin.common.config.PluginConfigCodec;
import com.ifielsc.ijob.plugin.support.RecordingInstanceLogs;

import org.junit.jupiter.api.Test;

import java.sql.SQLTimeoutException;

class BatchEtlTaskRunnerTest {
    @Test
    void stoppingAfterCommitStillReportsPartialWrites() {
        check("stop", InstanceStatus.STOPPED);
    }

    @Test
    void failedTruncateCanHaveEffectsWithoutCommittedBatches() {
        check("truncate", InstanceStatus.FAILED);
    }

    @Test
    void ambiguousCommitIsNotReportedAsRolledBack() {
        check("commit", InstanceStatus.FAILED);
    }

    @Test
    void queryTimeoutKeepsDistinctStatus() {
        check("timeout", InstanceStatus.TIMED_OUT);
    }

    private void check(String mode, InstanceStatus status) {
        var context =
                new TaskContext(
                        1L,
                        new ExecutionHandle(1L),
                        null,
                        new RecordingInstanceLogs(),
                        new VariableResolver());
        var executor =
                new BatchEtlExecutor(null, null, null) {
                    @Override
                    public BatchEtlResult execute(
                            BatchEtlTaskConfig config, TaskContext taskContext) {
                        switch (mode) {
                            case "stop" -> {
                                taskContext.putVariable("batchEtl.committedBatches", 1L);
                                throw new StopException();
                            }
                            case "truncate" ->
                                    taskContext.putVariable(
                                            "batchEtl.nonTransactionalClearAttempted", true);
                            case "commit" ->
                                    taskContext.putVariable("batchEtl.commitOutcomeUnknown", true);
                            case "timeout" -> {
                                throw new IllegalStateException(new SQLTimeoutException());
                            }
                            default -> throw new IllegalArgumentException();
                        }
                        throw new IllegalStateException("Controlled failure");
                    }
                };
        var runner =
                new BatchEtlTaskRunner(
                        new PluginConfigCodec(new ObjectMapper()),
                        new BatchEtlTaskConfigValidator(),
                        executor);
        var spec =
                new TaskExecutionSpec(
                        "test",
                        PluginType.BATCH_ETL,
                        "{\"sourceResourceId\":1,\"targetResourceId\":2,\"tables\":[\"orders\"],\"logicalKeys\":[\"id\"]}",
                        0,
                        0,
                        null,
                        true,
                        null);
        assertEquals(status, runner.run(spec, context).getStatus());
        assertEquals(
                !mode.equals("timeout"), context.getVariable("batchEtl.partialCommitPossible"));
    }
}
