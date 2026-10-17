package com.ifelio.ijob.core.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.contract.enums.RuntimeCommandType;
import com.ifelio.ijob.core.instance.InstanceService;
import com.ifelio.ijob.infra.coordination.command.DatabaseRuntimeCommandQueue;
import com.ifelio.ijob.infra.coordination.command.RuntimeCommandClaim;
import com.ifelio.ijob.infra.coordination.work.TriggerWorkClaim;
import com.ifelio.ijob.infra.entity.runtime.Instance;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

class RuntimeCommandProcessorTest {
    @Test
    void takeoverShouldAdoptTreeBeforeApplyingStopCommand() {
        TriggerWorkClaim workClaim =
                new TriggerWorkClaim(
                        7L, "node-b", "work-token", 2L, 2, 5, LocalDateTime.now().plusSeconds(30));
        RecordingInstances instances = new RecordingInstances();
        RecordingRuntime runtime = new RecordingRuntime(instances);
        RecordingQueue queue = new RecordingQueue();
        RuntimeCommandProcessor processor =
                new RuntimeCommandProcessor(
                        queue, null, runtime, instances, new ExecutionManager(), null);

        processor.process(workClaim);

        assertTrue(instances.adopted);
        assertTrue(runtime.stopped);
        assertEquals(2L, instances.root.getExecutionFence());
        assertTrue(queue.succeeded);
    }

    private static final class RecordingQueue extends DatabaseRuntimeCommandQueue {
        private boolean succeeded;

        private RecordingQueue() {
            super(null, null);
        }

        @Override
        public List<String> listDispatchable(TriggerWorkClaim claim) {
            return List.of("request-1");
        }

        @Override
        public RuntimeCommandClaim claim(String requestId, TriggerWorkClaim workClaim) {
            return new RuntimeCommandClaim(
                    requestId,
                    9L,
                    9L,
                    7L,
                    RuntimeCommandType.STOP,
                    workClaim.leaseOwner(),
                    "processing-token",
                    workClaim.fenceToken());
        }

        @Override
        public boolean succeed(RuntimeCommandClaim claim, String message) {
            succeeded = true;
            return true;
        }
    }

    private static final class RecordingInstances extends InstanceService {
        private final Instance root = new Instance();
        private boolean adopted;

        private RecordingInstances() {
            super(null, null);
            root.setId(9L);
            root.setRootInstanceId(9L);
            root.setTriggerId(7L);
            root.setExecutionFence(1L);
            root.setStatus(InstanceStatus.RUNNING);
        }

        @Override
        public Instance getInstance(Long id) {
            return root;
        }

        @Override
        public void adoptRoot(Long rootInstanceId, TriggerWorkClaim claim) {
            adopted = true;
            root.setExecutionFence(claim.fenceToken());
            root.setStatus(InstanceStatus.WAITING);
        }
    }

    private static final class RecordingRuntime extends RuntimeService {
        private final RecordingInstances instances;
        private boolean stopped;

        private RecordingRuntime(RecordingInstances instances) {
            super(null, null, null, null, null);
            this.instances = instances;
        }

        @Override
        public void stop(Long instanceId) {
            assertEquals(2L, instances.root.getExecutionFence());
            stopped = true;
        }
    }
}
