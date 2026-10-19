package com.ifielsc.ijob.core.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.exception.StopException;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.infra.entity.runtime.Instance;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class ExecutionTreeControlTest {
    @Test
    void timeoutShouldSignalRootAndStopActiveDescendants() {
        ExecutionManager manager = new ExecutionManager();
        ExecutionHandle root = manager.register(10L);
        ExecutionHandle child = manager.register(11L);
        RecordingInstances instances =
                new RecordingInstances(
                        Map.of(
                                10L, List.of(instance(11L, InstanceStatus.RUNNING)),
                                11L, List.of(instance(12L, InstanceStatus.WAITING))));
        ExecutionTreeControl control = new ExecutionTreeControl(manager, instances);

        control.requestTimeout(10L);

        assertTrue(root.isTimeoutRequested());
        assertTrue(child.isStopRequested());
        assertEquals(List.of(12L, 11L), instances.stoppedIds);
    }

    @Test
    void failureAbortShouldStopDescendantsWithoutStoppingRoot() {
        ExecutionManager manager = new ExecutionManager();
        ExecutionHandle root = manager.register(20L);
        ExecutionHandle child = manager.register(21L);
        RecordingInstances instances =
                new RecordingInstances(Map.of(20L, List.of(instance(21L, InstanceStatus.RUNNING))));
        ExecutionTreeControl control = new ExecutionTreeControl(manager, instances);

        control.requestAbortDescendants(20L);

        assertFalse(root.isStopRequested());
        assertTrue(child.isStopRequested());
        assertEquals(List.of(21L), instances.stoppedIds);
    }

    @Test
    void pauseAndResumeShouldPropagateThroughActiveTree() {
        ExecutionManager manager = new ExecutionManager();
        ExecutionHandle root = manager.register(30L);
        ExecutionHandle child = manager.register(31L);
        ExecutionHandle grandchild = manager.register(32L);
        RecordingInstances instances =
                new RecordingInstances(
                        Map.of(
                                30L, List.of(instance(31L, InstanceStatus.RUNNING)),
                                31L, List.of(instance(32L, InstanceStatus.RUNNING))));
        ExecutionTreeControl control = new ExecutionTreeControl(manager, instances);

        assertTrue(control.requestPause(30L));
        assertTrue(root.isPauseRequested());
        assertTrue(child.isPauseRequested());
        assertTrue(grandchild.isPauseRequested());
        assertEquals(List.of(31L, 32L), instances.pausedIds);

        assertTrue(control.requestResume(30L));
        assertFalse(root.isPauseRequested());
        assertFalse(child.isPauseRequested());
        assertFalse(grandchild.isPauseRequested());
        assertEquals(List.of(32L, 31L), instances.resumedIds);
    }

    @Test
    void pauseFailureShouldRollbackSignalsAndPersistedChildren() {
        ExecutionManager manager = new ExecutionManager();
        ExecutionHandle root = manager.register(40L);
        ExecutionHandle child = manager.register(41L);
        ExecutionHandle pausedSibling = manager.register(43L);
        pausedSibling.requestPause();
        RecordingInstances instances =
                new RecordingInstances(
                        Map.of(
                                40L,
                                        List.of(
                                                instance(43L, InstanceStatus.PAUSED),
                                                instance(41L, InstanceStatus.RUNNING)),
                                41L, List.of(instance(42L, InstanceStatus.RUNNING))));
        instances.pauseFailureId = 42L;
        ExecutionTreeControl control = new ExecutionTreeControl(manager, instances);

        assertThrows(IllegalStateException.class, () -> control.requestPause(40L));

        assertFalse(root.isPauseRequested());
        assertFalse(child.isPauseRequested());
        assertTrue(pausedSibling.isPauseRequested());
        assertEquals(List.of(41L), instances.resumedIds);
    }

    @Test
    void timeoutShouldWakePausedExecutionAndStopDescendants() throws InterruptedException {
        ExecutionManager manager = new ExecutionManager();
        ExecutionHandle root = manager.register(50L);
        ExecutionHandle child = manager.register(51L);
        RecordingInstances instances =
                new RecordingInstances(Map.of(50L, List.of(instance(51L, InstanceStatus.RUNNING))));
        ExecutionTreeControl control = new ExecutionTreeControl(manager, instances);
        ExecutorService waiter = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);

        try {
            assertTrue(control.requestPause(50L));
            Future<?> paused =
                    waiter.submit(
                            () -> {
                                started.countDown();
                                root.checkPause();
                            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertFalse(paused.isDone());

            control.requestTimeout(50L);

            ExecutionException exception =
                    assertThrows(ExecutionException.class, () -> paused.get(2, TimeUnit.SECONDS));
            assertTrue(exception.getCause() instanceof StopException);
            assertTrue(root.isTimeoutRequested());
            assertFalse(root.isPauseRequested());
            assertTrue(child.isStopRequested());
            assertEquals(List.of(51L), instances.stoppedIds);
        } finally {
            waiter.shutdownNow();
            assertTrue(waiter.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void activeGateShouldInspectDescendantHandles() {
        ExecutionManager manager = new ExecutionManager();
        RecordingInstances instances =
                new RecordingInstances(
                        Map.of(
                                60L, List.of(instance(61L, InstanceStatus.FAILED)),
                                61L, List.of(instance(62L, InstanceStatus.STOPPED))));
        ExecutionTreeControl control = new ExecutionTreeControl(manager, instances);

        assertFalse(control.hasActiveExecutionInTree(60L));
        manager.register(62L);
        assertTrue(control.hasActiveExecutionInTree(60L));
    }

    private static Instance instance(Long id, InstanceStatus status) {
        Instance instance = new Instance();
        instance.setId(id);
        instance.setStatus(status);
        return instance;
    }

    private static final class RecordingInstances extends InstanceService {
        private final Map<Long, List<Instance>> children;
        private final List<Long> stoppedIds = new ArrayList<>();
        private final List<Long> pausedIds = new ArrayList<>();
        private final List<Long> resumedIds = new ArrayList<>();
        private Long pauseFailureId;

        private RecordingInstances(Map<Long, List<Instance>> children) {
            super(null, null, null, null);
            this.children = children;
        }

        @Override
        public List<Instance> listChildren(Long parentId) {
            return children.getOrDefault(parentId, List.of());
        }

        @Override
        public void stop(Long instanceId) {
            stoppedIds.add(instanceId);
        }

        @Override
        public boolean pauseIfRunning(Long instanceId) {
            if (instanceId.equals(pauseFailureId)) {
                throw new IllegalStateException("pause persistence failed");
            }
            pausedIds.add(instanceId);
            updateStatus(instanceId, InstanceStatus.PAUSED);
            return true;
        }

        @Override
        public boolean resumeIfPaused(Long instanceId) {
            resumedIds.add(instanceId);
            updateStatus(instanceId, InstanceStatus.RUNNING);
            return true;
        }

        private void updateStatus(Long instanceId, InstanceStatus status) {
            children.values().stream()
                    .flatMap(List::stream)
                    .filter(instance -> instanceId.equals(instance.getId()))
                    .forEach(instance -> instance.setStatus(status));
        }
    }
}
