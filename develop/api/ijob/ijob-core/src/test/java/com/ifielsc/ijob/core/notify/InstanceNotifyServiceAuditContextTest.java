package com.ifielsc.ijob.core.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.NotifyPolicy;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.core.job.definition.JobExecutionSpec;
import com.ifielsc.ijob.core.runtime.definition.RuntimeDefinitionService;
import com.ifielsc.ijob.core.task.definition.TaskAdmissionSnapshot;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;
import com.ifielsc.ijob.infra.entity.notify.NotifyBinding;
import com.ifielsc.ijob.infra.entity.runtime.Instance;

import org.junit.jupiter.api.Test;

import java.util.List;

class InstanceNotifyServiceAuditContextTest {
    @Test
    void rootTaskNotificationShouldCarryBindingAndInstanceSnapshot() {
        Instance instance = new Instance();
        instance.setId(81L);
        instance.setTargetType(TargetType.TASK);
        instance.setTargetId(82L);
        instance.setTaskDefinitionId(41L);
        instance.setStatus(InstanceStatus.SUCCESS);
        NotifyBinding binding = new NotifyBinding();
        binding.setId(83L);
        binding.setNotifyConfigId(84L);
        binding.setNotifyPolicy(NotifyPolicy.ALWAYS);

        RecordingNotificationService notifications = new RecordingNotificationService();
        InstanceNotifyService service =
                new InstanceNotifyService(
                        new StubInstanceService(instance),
                        new StubRuntimeDefinitionService(),
                        notifications,
                        new StubBindingService(binding));

        service.notifyIfNecessary(81L);

        assertEquals(84L, notifications.notifyConfigId);
        assertEquals(83L, notifications.notifyBindingId);
        assertEquals(81L, notifications.context.getInstanceId());
        assertEquals(TargetType.TASK, notifications.context.getTargetType());
        assertEquals(82L, notifications.context.getTargetId());
        assertSame(instance.getStatus(), notifications.context.getStatus());
    }

    @Test
    void timedOutInstanceShouldMatchFailureNotificationPolicy() {
        Instance instance = new Instance();
        instance.setId(91L);
        instance.setTargetType(TargetType.TASK);
        instance.setTargetId(92L);
        instance.setTaskDefinitionId(41L);
        instance.setStatus(InstanceStatus.TIMED_OUT);
        NotifyBinding binding = new NotifyBinding();
        binding.setId(93L);
        binding.setNotifyConfigId(94L);
        binding.setNotifyPolicy(NotifyPolicy.ON_FAILURE);
        RecordingNotificationService notifications = new RecordingNotificationService();
        InstanceNotifyService service =
                new InstanceNotifyService(
                        new StubInstanceService(instance),
                        new StubRuntimeDefinitionService(),
                        notifications,
                        new StubBindingService(binding));

        service.notifyIfNecessary(91L);

        assertEquals(94L, notifications.notifyConfigId);
        assertSame(InstanceStatus.TIMED_OUT, notifications.context.getStatus());
    }

    private static final class StubInstanceService extends InstanceService {
        private final Instance instance;

        private StubInstanceService(Instance instance) {
            super(null, null, null, null);
            this.instance = instance;
        }

        @Override
        public Instance getInstance(Long id) {
            return instance;
        }
    }

    private static final class StubRuntimeDefinitionService extends RuntimeDefinitionService {
        private StubRuntimeDefinitionService() {
            super(null, null);
        }

        @Override
        public TaskAdmissionSnapshot loadTask(Long taskId, Long taskDefinitionId) {
            TaskExecutionSpec spec =
                    new TaskExecutionSpec(
                            "task-82", PluginType.SCRIPT, "config", 0, 0, null, true, null);
            return new TaskAdmissionSnapshot(taskId, taskDefinitionId, 3L, spec);
        }

        @Override
        public JobExecutionSpec loadJob(Long jobId, Long jobDefinitionId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubBindingService extends NotifyBindingService {
        private final NotifyBinding binding;

        private StubBindingService(NotifyBinding binding) {
            super(null, null, null, null);
            this.binding = binding;
        }

        @Override
        public List<NotifyBinding> listNotifyBindingsByTargetId(
                TargetType targetType, Long targetId) {
            return List.of(binding);
        }
    }

    private static final class RecordingNotificationService extends NotificationService {
        private Long notifyConfigId;
        private NotificationContext context;
        private Long notifyBindingId;

        private RecordingNotificationService() {
            super(null, null, null);
        }

        @Override
        public NotifyDispatchResult send(
                Long configId, NotificationContext notificationContext, Long bindingId) {
            notifyConfigId = configId;
            context = notificationContext;
            notifyBindingId = bindingId;
            return new NotifyDispatchResult(NotifyDispatchStatus.DELIVERED, null);
        }
    }
}
