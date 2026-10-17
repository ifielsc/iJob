package com.ifelio.ijob.core.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.core.instance.InstanceService;
import com.ifelio.ijob.infra.entity.runtime.Instance;

import org.junit.jupiter.api.Test;

class RuntimeServiceTest {
    @Test
    void stopShouldNotifyThroughTransactionalRuntimeAndIgnoreRepeatedStop() {
        RecordingInstanceService instances = instances(TargetType.TASK, InstanceStatus.RUNNING);
        ExecutionManager manager = new ExecutionManager();
        java.util.List<Long> notified = new java.util.ArrayList<>();
        var notifier =
                new com.ifelio.ijob.core.notify.InstanceNotifyService(null, null, null, null) {
                    @Override
                    public void notifyIfNecessary(Long id) {
                        assertEquals(InstanceStatus.STOPPED, instances.instance.getStatus());
                        notified.add(id);
                    }
                };
        RuntimeService service =
                new RuntimeService(
                        manager,
                        new ExecutionTreeControl(manager, instances),
                        instances,
                        new RecordingTaskRetry(),
                        new RecordingJobRetry(),
                        (com.ifelio.ijob.core.event.EventWaitResolutionService) null,
                        new InstanceRuntimeService(instances, notifier));

        service.stop(9L);
        service.stop(9L);

        assertEquals(java.util.List.of(9L), notified);
    }

    @Test
    void stopShouldPersistWaitingInstanceWithoutLocalHandle() {
        RecordingInstanceService instances = instances(TargetType.TASK, InstanceStatus.WAITING);
        ExecutionManager manager = new ExecutionManager();
        RuntimeService service = service(manager, instances);

        service.stop(9L);

        assertEquals(InstanceStatus.STOPPED, instances.instance.getStatus());
        assertFalse(manager.hasHandle(9L));
    }

    @Test
    void pauseAndResumeShouldRequireAndControlLocalHandle() {
        RecordingInstanceService instances = instances(TargetType.JOB, InstanceStatus.RUNNING);
        ExecutionManager manager = new ExecutionManager();
        ExecutionHandle handle = manager.register(9L);
        RuntimeService service = service(manager, instances);

        service.pause(9L);
        assertTrue(handle.isPauseRequested());
        assertEquals(InstanceStatus.PAUSED, instances.instance.getStatus());

        service.resume(9L);
        assertFalse(handle.isPauseRequested());
        assertEquals(InstanceStatus.RUNNING, instances.instance.getStatus());
    }

    @Test
    void pauseShouldRejectOrphanedRunningInstance() {
        RecordingInstanceService instances = instances(TargetType.TASK, InstanceStatus.RUNNING);
        RuntimeService service = service(new ExecutionManager(), instances);

        BusinessException error = assertThrows(BusinessException.class, () -> service.pause(9L));

        assertEquals(ResultCode.CONFLICT, error.getCode());
        assertEquals(InstanceStatus.RUNNING, instances.instance.getStatus());
    }

    @Test
    void retryShouldRouteFailedOrTimedOutTaskOrJob() {
        RecordingInstanceService instances = instances(TargetType.TASK, InstanceStatus.FAILED);
        RecordingTaskRetry tasks = new RecordingTaskRetry();
        RecordingJobRetry jobs = new RecordingJobRetry();
        RuntimeService service =
                new RuntimeService(
                        new ExecutionManager(),
                        new ExecutionTreeControl(new ExecutionManager(), instances),
                        instances,
                        tasks,
                        jobs);

        service.retry(9L);
        assertEquals(9L, tasks.id);

        instances.instance.setTargetType(TargetType.JOB);
        service.retry(9L);
        assertEquals(9L, jobs.id);

        instances.instance.setTargetType(TargetType.TASK);
        instances.instance.setStatus(InstanceStatus.TIMED_OUT);
        service.retry(9L);
        assertEquals(9L, tasks.id);

        instances.instance.setStatus(InstanceStatus.SUCCESS);
        BusinessException error = assertThrows(BusinessException.class, () -> service.retry(9L));
        assertEquals(ResultCode.CONFLICT, error.getCode());
    }

    @Test
    void retryShouldRejectWhileLocalExecutionHandleExists() {
        RecordingInstanceService instances = instances(TargetType.JOB, InstanceStatus.FAILED);
        ExecutionManager manager = new ExecutionManager();
        manager.register(9L);
        RuntimeService service = service(manager, instances);

        BusinessException error = assertThrows(BusinessException.class, () -> service.retry(9L));

        assertEquals(ResultCode.CONFLICT, error.getCode());
    }

    @Test
    void retryShouldRejectNestedInstanceAndActiveDescendant() {
        RecordingInstanceService nested = instances(TargetType.JOB, InstanceStatus.FAILED);
        nested.instance.setParentInstanceId(8L);
        nested.instance.setRootInstanceId(8L);
        RuntimeService nestedService = service(new ExecutionManager(), nested);

        BusinessException nestedError =
                assertThrows(BusinessException.class, () -> nestedService.retry(9L));
        assertEquals(ResultCode.CONFLICT, nestedError.getCode());

        RecordingInstanceService root = instances(TargetType.JOB, InstanceStatus.FAILED);
        Instance child = new Instance();
        child.setId(10L);
        child.setStatus(InstanceStatus.FAILED);
        root.child = child;
        ExecutionManager manager = new ExecutionManager();
        manager.register(10L);
        RuntimeService rootService = service(manager, root);

        BusinessException activeError =
                assertThrows(BusinessException.class, () -> rootService.retry(9L));
        assertEquals(ResultCode.CONFLICT, activeError.getCode());
    }

    private RuntimeService service(ExecutionManager manager, RecordingInstanceService instances) {
        return new RuntimeService(
                manager,
                new ExecutionTreeControl(manager, instances),
                instances,
                new RecordingTaskRetry(),
                new RecordingJobRetry());
    }

    private RecordingInstanceService instances(TargetType type, InstanceStatus status) {
        Instance instance = new Instance();
        instance.setId(9L);
        instance.setRootInstanceId(9L);
        instance.setTargetType(type);
        instance.setStatus(status);
        return new RecordingInstanceService(instance);
    }

    private static final class RecordingInstanceService extends InstanceService {
        private final Instance instance;
        private Instance child;

        private RecordingInstanceService(Instance instance) {
            super(null, null, null, null);
            this.instance = instance;
        }

        @Override
        public Instance getInstance(Long id) {
            return instance;
        }

        @Override
        public java.util.List<Instance> listChildren(Long id) {
            return child != null && instance.getId().equals(id)
                    ? java.util.List.of(child)
                    : java.util.List.of();
        }

        @Override
        public void stop(Long id) {
            instance.setStatus(InstanceStatus.STOPPED);
        }

        @Override
        public void pause(Long id) {
            instance.setStatus(InstanceStatus.PAUSED);
        }

        @Override
        public void resume(Long id) {
            instance.setStatus(InstanceStatus.RUNNING);
        }
    }

    private static final class RecordingTaskRetry extends TaskRetryService {
        private Long id;

        private RecordingTaskRetry() {
            super(null, null);
        }

        @Override
        public void retry(Long instanceId) {
            id = instanceId;
        }
    }

    private static final class RecordingJobRetry extends JobRetryService {
        private Long id;

        private RecordingJobRetry() {
            super(null, null, null, null, null, null);
        }

        @Override
        public void retryFailedNodes(Long instanceId) {
            id = instanceId;
        }
    }
}
