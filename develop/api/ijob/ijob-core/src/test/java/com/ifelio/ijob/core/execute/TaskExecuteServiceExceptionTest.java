package com.ifelio.ijob.core.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifelio.ijob.common.exception.StopException;
import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.execute.task.TaskResult;
import com.ifelio.ijob.core.execute.task.TaskRunner;
import com.ifelio.ijob.core.execute.variable.VariableResolver;
import com.ifelio.ijob.core.task.definition.TaskExecutionSpec;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

class TaskExecuteServiceExceptionTest {
    @Test
    void runnerStopAndTimeoutMustNotBeWrappedOrRetried() throws Exception {
        for (RuntimeException failure :
                new RuntimeException[] {
                    new StopException(), new ExecutionTimeoutException("synthetic", null)
                }) {
            AtomicInteger attempts = new AtomicInteger();
            TaskRunner runner =
                    new TaskRunner() {
                        @Override
                        public PluginType supportType() {
                            return PluginType.SCRIPT;
                        }

                        @Override
                        public TaskResult run(TaskExecutionSpec spec, TaskContext context) {
                            attempts.incrementAndGet();
                            throw failure;
                        }
                    };
            try (var executor = Executors.newSingleThreadExecutor()) {
                TaskExecuteService service =
                        new TaskExecuteService(
                                null, null, null, null, null, null, null, null, null, null,
                                executor);
                var method =
                        TaskExecuteService.class.getDeclaredMethod(
                                "executeWithRetry",
                                TaskRunner.class,
                                TaskExecutionSpec.class,
                                TaskContext.class);
                method.setAccessible(true);
                TaskExecutionSpec spec =
                        new TaskExecutionSpec(
                                "test", PluginType.SCRIPT, "{}", 2, 0, null, true, null);
                TaskContext context =
                        new TaskContext(
                                1L, new ExecutionHandle(1L), null, null, new VariableResolver());
                InvocationTargetException exception =
                        assertThrows(
                                InvocationTargetException.class,
                                () -> method.invoke(service, runner, spec, context));
                assertSame(failure, exception.getCause());
                assertEquals(1, attempts.get());
            }
        }
    }
}
