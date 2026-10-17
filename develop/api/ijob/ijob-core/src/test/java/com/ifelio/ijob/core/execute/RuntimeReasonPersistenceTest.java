package com.ifelio.ijob.core.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.core.execute.task.TaskResult;

import org.junit.jupiter.api.Test;

class RuntimeReasonPersistenceTest {
    @Test
    void invalidPluginResultsMustCarryStableReasonMetadata() throws Exception {
        var runtime = new RecordingRuntime();
        var service =
                new TaskExecuteService(
                        null, null, null, null, null, runtime, null, null, null, null, null);
        var complete =
                TaskExecuteService.class.getDeclaredMethod(
                        "complete", Long.class, TaskResult.class);
        complete.setAccessible(true);
        complete.invoke(service, 1L, null);
        assertNotNull(runtime.result.getMessageMetadata());
        assertEquals(
                "runtime.reason.task-result-missing",
                runtime.result.getMessageMetadata().messageKey());
        complete.invoke(service, 1L, TaskResult.builder().status(InstanceStatus.RUNNING).build());
        assertEquals(
                "runtime.reason.task-result-invalid",
                runtime.result.getMessageMetadata().messageKey());
    }

    private static final class RecordingRuntime extends InstanceRuntimeService {
        private TaskResult result;

        RecordingRuntime() {
            super(null, null);
        }

        @Override
        public void fail(Long id, String message) {
            result = TaskResult.fail(message);
        }

        @Override
        public void completeTask(Long id, TaskResult result) {
            this.result = result;
        }
    }
}
