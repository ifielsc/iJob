package com.ifielsc.ijob.core.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.core.job.definition.JobExecutionSpec;
import com.ifielsc.ijob.core.job.definition.JobRuntimeNestingGuard;
import com.ifielsc.ijob.infra.entity.runtime.Instance;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;

class JobFailureSanitizationTest {
    @Test
    void internalFailureNeverBecomesPersistedDisplayText() {
        var persisted = new java.util.concurrent.atomic.AtomicBoolean();
        var runtime =
                new InstanceRuntimeService(null, null) {
                    @Override
                    public void running(Long id) {}

                    @Override
                    public void fail(Long id, String message) {
                        assertNull(
                                message,
                                "generic status metadata must provide the translated message");
                        persisted.set(true);
                    }
                };
        var instances =
                new InstanceService(null, null, null, null) {
                    @Override
                    public Instance getInstance(Long id) {
                        var instance = new Instance();
                        instance.setId(id);
                        return instance;
                    }

                    @Override
                    public List<Instance> listChildren(Long id) {
                        throw new IllegalStateException("jdbc:canary-secret");
                    }
                };
        var guard =
                new JobRuntimeNestingGuard(null) {
                    @Override
                    public void validateInstance(Instance instance) {}
                };
        try (var executor = Executors.newSingleThreadExecutor()) {
            var service =
                    new JobExecuteService(
                            new VariableResolver(),
                            guard,
                            null,
                            null,
                            null,
                            null,
                            null,
                            instances,
                            new com.ifielsc.ijob.core.instance.InstanceLogService(null) {
                                @Override
                                public void error(Long id, String content) {
                                    assertTrue(content.contains("orchestration failed"));
                                    org.junit.jupiter.api.Assertions.assertFalse(
                                            content.contains("canary-secret"));
                                }
                            },
                            runtime,
                            new ExecutionManager(),
                            null,
                            executor);
            ExecutionFailedException failure =
                    assertThrows(
                            ExecutionFailedException.class,
                            () ->
                                    service.executeRetry(
                                            1L,
                                            new JobExecutionSpec(1L, 2L, 1L, "test", 5),
                                            null,
                                            null));
            assertEquals("Job execution failed", failure.getMessage());
            assertTrue(persisted.get());
        }
    }
}
