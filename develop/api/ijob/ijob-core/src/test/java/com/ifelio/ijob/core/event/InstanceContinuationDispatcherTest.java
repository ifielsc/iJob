package com.ifelio.ijob.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.core.execute.JobExecuteService;
import com.ifelio.ijob.core.instance.InstanceService;
import com.ifelio.ijob.core.runtime.RuntimeErrorMetadata;
import com.ifelio.ijob.core.trigger.TriggerCommand;
import com.ifelio.ijob.core.trigger.TriggerCommandHandler;
import com.ifelio.ijob.core.trigger.TriggerRuntimeService;
import com.ifelio.ijob.infra.coordination.work.DatabaseTriggerWorkQueue;
import com.ifelio.ijob.infra.coordination.work.ExecutionFenceCoordinator;
import com.ifelio.ijob.infra.coordination.work.SuspendedWorkPermit;
import com.ifelio.ijob.infra.coordination.work.TriggerWorkItem;
import com.ifelio.ijob.infra.coordination.work.TriggerWorkStatus;
import com.ifelio.ijob.infra.entity.runtime.Instance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class InstanceContinuationDispatcherTest {
    @Test
    void committedContinuationShouldScheduleClaimOutsideSubmittingTransaction() {
        AppProperties properties = new AppProperties();
        properties.getRuntime().setMode(AppProperties.RuntimeMode.STANDALONE);
        List<Runnable> scheduled = new ArrayList<>();
        RecordingTriggerHandler handler = new RecordingTriggerHandler();
        InstanceContinuationDispatcher dispatcher =
                new InstanceContinuationDispatcher(
                        scheduled::add,
                        new RecordingInstanceService(),
                        provider(new FailingJobExecuteService()),
                        new RecordingTriggerRuntimeService(),
                        handler,
                        new RecordingWorkQueue(),
                        new ExecutionFenceCoordinator(properties));
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            dispatcher.dispatchAfterCommit(1L);
            assertEquals(0, scheduled.size());
            TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit();
            assertNull(handler.triggerId);
            assertEquals(1, scheduled.size());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        scheduled.getFirst().run();
        assertEquals(9L, handler.triggerId);
    }

    @Test
    void continuationFailureShouldFailRootAndCompleteTrigger() {
        RecordingInstanceService instances = new RecordingInstanceService();
        RecordingTriggerRuntimeService triggers = new RecordingTriggerRuntimeService();
        InstanceContinuationDispatcher dispatcher =
                new InstanceContinuationDispatcher(
                        Runnable::run,
                        instances,
                        provider(new FailingJobExecuteService()),
                        triggers);

        dispatcher.dispatchAfterCommit(1L);

        assertEquals(InstanceStatus.FAILED, instances.root.getStatus());
        assertEquals(9L, triggers.failedTriggerId);
        assertEquals("Event continuation failed", triggers.message);
    }

    @Test
    void persistentContinuationShouldResumeWorkBeforeQueueDispatch() {
        AppProperties properties = new AppProperties();
        properties.getRuntime().setMode(AppProperties.RuntimeMode.STANDALONE);
        ExecutionFenceCoordinator fenceCoordinator = new ExecutionFenceCoordinator(properties);
        RecordingInstanceService instances = new RecordingInstanceService();
        RecordingWorkQueue workQueue = new RecordingWorkQueue();
        RecordingTriggerHandler handler = new RecordingTriggerHandler();
        InstanceContinuationDispatcher dispatcher =
                new InstanceContinuationDispatcher(
                        Runnable::run,
                        instances,
                        provider(new FailingJobExecuteService()),
                        new RecordingTriggerRuntimeService(),
                        handler,
                        workQueue,
                        fenceCoordinator);

        dispatcher.dispatchAfterCommit(1L);

        assertEquals(1, instances.claimCalls);
        assertEquals(9L, workQueue.resumedTriggerId);
        assertEquals(9L, handler.triggerId);
    }

    private static ObjectProvider<JobExecuteService> provider(JobExecuteService service) {
        return new ObjectProvider<>() {
            @Override
            public JobExecuteService getObject(Object... args) {
                return service;
            }

            @Override
            public JobExecuteService getIfAvailable() {
                return service;
            }

            @Override
            public JobExecuteService getIfUnique() {
                return service;
            }

            @Override
            public JobExecuteService getObject() {
                return service;
            }

            @Override
            public Iterator<JobExecuteService> iterator() {
                return List.of(service).iterator();
            }
        };
    }

    private static final class RecordingInstanceService extends InstanceService {
        private final Instance root = root();
        private int claimCalls;

        private RecordingInstanceService() {
            super(null, null);
        }

        @Override
        public Instance getInstance(Long id) {
            return root;
        }

        @Override
        public boolean claimContinuation(Long instanceId) {
            claimCalls++;
            root.setStatus(InstanceStatus.WAITING);
            return true;
        }

        @Override
        public boolean completeTask(
                Long instanceId,
                InstanceStatus status,
                String message,
                RuntimeErrorMetadata metadata) {
            assertEquals("runtime.reason.event-continuation-failed", metadata.messageKey());
            root.setMessageKey(metadata.messageKey());
            root.setStatus(InstanceStatus.FAILED);
            root.setMessage(message);
            return true;
        }

        private static Instance root() {
            Instance instance = new Instance();
            instance.setId(1L);
            instance.setRootInstanceId(1L);
            instance.setTargetType(TargetType.JOB);
            instance.setTriggerId(9L);
            instance.setStatus(InstanceStatus.WAITING_EVENT);
            return instance;
        }
    }

    private static final class RecordingWorkQueue extends DatabaseTriggerWorkQueue {
        private Long resumedTriggerId;

        private RecordingWorkQueue() {
            super(null, null);
        }

        @Override
        public TriggerWorkItem get(Long triggerId) {
            TriggerWorkItem item = new TriggerWorkItem();
            item.setTriggerId(triggerId);
            item.setStatus(TriggerWorkStatus.SUSPENDED);
            item.setFenceToken(3L);
            return item;
        }

        @Override
        public boolean resumeSuspended(SuspendedWorkPermit permit) {
            resumedTriggerId = permit.triggerId();
            return true;
        }
    }

    private static final class RecordingTriggerHandler extends TriggerCommandHandler {
        private Long triggerId;

        private RecordingTriggerHandler() {
            super(Runnable::run, null, null);
        }

        @Override
        public void handle(TriggerCommand command) {
            triggerId = command.getTriggerId();
        }
    }

    private static final class FailingJobExecuteService extends JobExecuteService {
        private FailingJobExecuteService() {
            super(null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public void execute(Long instanceId) {
            throw new IllegalStateException("boom");
        }
    }

    private static final class RecordingTriggerRuntimeService extends TriggerRuntimeService {
        private Long failedTriggerId;
        private String message;

        private RecordingTriggerRuntimeService() {
            super(null);
        }

        @Override
        public void completeFromRoot(Long id, Instance root) {
            assertEquals("runtime.reason.event-continuation-failed", root.getMessageKey());
            failedTriggerId = id;
            message = root.getMessage();
        }
    }
}
