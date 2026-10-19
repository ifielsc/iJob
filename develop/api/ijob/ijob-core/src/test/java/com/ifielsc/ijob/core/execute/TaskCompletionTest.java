package com.ifielsc.ijob.core.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.core.execute.task.TaskResult;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.core.notify.InstanceNotifyService;
import com.ifielsc.ijob.core.runtime.RuntimeErrorMetadata;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

class TaskCompletionTest {
    @Test
    void terminalStateAndNotificationEnqueueShouldShareOneTransactionBoundary() throws Exception {
        for (String methodName :
                new String[] {"completeTask", "success", "fail", "stop", "timeout", "skipped"}) {
            Class<?>[] parameters =
                    switch (methodName) {
                        case "completeTask" -> new Class<?>[] {Long.class, TaskResult.class};
                        case "fail", "timeout" -> new Class<?>[] {Long.class, String.class};
                        default -> new Class<?>[] {Long.class};
                    };
            Transactional annotation =
                    InstanceRuntimeService.class
                            .getMethod(methodName, parameters)
                            .getAnnotation(Transactional.class);
            assertNotNull(annotation, methodName + " must own the terminal/outbox transaction");
        }
    }

    @Test
    void duplicateCompletionDoesNotNotifyTwice() {
        var notifications = new AtomicInteger();
        var instances =
                new InstanceService(null, null) {
                    private boolean completed;

                    @Override
                    public boolean completeTask(
                            Long id,
                            InstanceStatus status,
                            String message,
                            RuntimeErrorMetadata metadata) {
                        if (completed) return false;
                        completed = true;
                        return true;
                    }
                };
        var notifier =
                new InstanceNotifyService(null, null, null, null) {
                    @Override
                    public void notifyIfNecessary(Long id) {
                        notifications.incrementAndGet();
                    }
                };
        var runtime = new InstanceRuntimeService(instances, notifier);
        runtime.completeTask(1L, TaskResult.success("done"));
        runtime.completeTask(1L, TaskResult.success("done"));
        assertEquals(1, notifications.get());
    }

    @Test
    void missingRunnerResultIsNotSuccessful() throws Exception {
        var failed = new AtomicInteger();
        var runtime =
                new InstanceRuntimeService(null, null) {
                    @Override
                    public void completeTask(Long id, TaskResult result) {
                        assertEquals(InstanceStatus.FAILED, result.getStatus());
                        assertEquals(
                                "runtime.reason.task-result-missing",
                                result.getMessageMetadata().messageKey());
                        failed.incrementAndGet();
                    }

                    @Override
                    public void success(Long id) {
                        org.junit.jupiter.api.Assertions.fail("An absent result must not succeed");
                    }
                };
        var service =
                new TaskExecuteService(
                        null, null, null, null, null, runtime, null, null, null, null, null);
        var complete =
                TaskExecuteService.class.getDeclaredMethod(
                        "complete", Long.class, TaskResult.class);
        complete.setAccessible(true);
        complete.invoke(service, 1L, null);
        complete.invoke(service, 1L, new TaskResult());
        assertEquals(2, failed.get());
    }
}
