package com.ifelio.ijob.plugin.datacheck.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.contract.enums.NotifySendSource;
import com.ifelio.ijob.core.execute.ExecutionHandle;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.notify.NotificationService;
import com.ifelio.ijob.core.notify.NotifyDispatchContext;
import com.ifelio.ijob.core.notify.NotifyDispatchResult;
import com.ifelio.ijob.core.notify.NotifyDispatchStatus;
import com.ifelio.ijob.core.notify.NotifyMessage;
import com.ifelio.ijob.core.notify.template.NotificationMessages;
import com.ifelio.ijob.core.notify.template.PluginNotification;
import com.ifelio.ijob.plugin.datacheck.config.CheckLevel;
import com.ifelio.ijob.plugin.datacheck.config.HandlerConfig;
import com.ifelio.ijob.plugin.datacheck.model.DataCheckContext;
import com.ifelio.ijob.plugin.datacheck.model.DataCheckItemResult;
import com.ifelio.ijob.plugin.datacheck.model.DataCheckResult;
import com.ifelio.ijob.plugin.support.RecordingInstanceLogs;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class DataCheckHandlerTest {
    @Test
    void notifyShouldSkipAllPassByDefault() {
        RecordingNotificationService notifications = new RecordingNotificationService();
        NotifyDataCheckHandler handler =
                new NotifyDataCheckHandler(
                        notifications,
                        new NotificationMessages(new AppProperties()),
                        new PluginNotification(
                                new NotificationMessages(new AppProperties()), null, null));

        handler.handle(notifyConfig(false), result(true, true), context());

        assertEquals(0, notifications.calls);
    }

    @Test
    void warningFailureShouldNotifyWithAuditedSourceContext() {
        RecordingNotificationService notifications = new RecordingNotificationService();
        NotifyDataCheckHandler handler =
                new NotifyDataCheckHandler(
                        notifications,
                        new NotificationMessages(new AppProperties()),
                        new PluginNotification(
                                new NotificationMessages(new AppProperties()), null, null));

        handler.handle(notifyConfig(false), result(true, false), context());

        assertEquals(1, notifications.calls);
        assertEquals(51L, notifications.configId);
        assertNotNull(notifications.message);
        assertEquals(NotifySendSource.DATA_CHECK, notifications.context.getSourceType());
        assertEquals(41L, notifications.context.getInstanceId());
    }

    @Test
    void notifyOnSuccessShouldSendAllPassResult() {
        RecordingNotificationService notifications = new RecordingNotificationService();
        NotifyDataCheckHandler handler =
                new NotifyDataCheckHandler(
                        notifications,
                        new NotificationMessages(new AppProperties()),
                        new PluginNotification(
                                new NotificationMessages(new AppProperties()), null, null));

        handler.handle(notifyConfig(true), result(true, true), context());

        assertEquals(1, notifications.calls);
    }

    @Test
    void notificationUsesComparisonConditionAndPreservesSqlNull() {
        RecordingNotificationService notifications = new RecordingNotificationService();
        var messages = new NotificationMessages(new AppProperties());
        var handler =
                new NotifyDataCheckHandler(
                        notifications,
                        messages,
                        new com.ifelio.ijob.core.notify.template.PluginNotification(
                                messages, null, null));
        var check = new com.ifelio.ijob.plugin.datacheck.config.DataCheckItemConfig();
        check.setName("check-one");
        check.setCompareType(com.ifelio.ijob.plugin.datacheck.config.CompareType.NE);
        var context =
                DataCheckContext.builder()
                        .instanceId(41L)
                        .resourceId(9L)
                        .checks(List.of(check))
                        .build();
        var item =
                DataCheckItemResult.failed(
                        "check-one", null, null, "comparison failed", CheckLevel.ERROR);
        handler.handle(notifyConfig(false), DataCheckResult.from(List.of(item), false), context);
        org.junit.jupiter.api.Assertions.assertTrue(
                notifications.message.getContent().contains("Not equal to null"));
        org.junit.jupiter.api.Assertions.assertTrue(
                notifications.message.getContent().contains("Data source"));
        org.junit.jupiter.api.Assertions.assertTrue(
                notifications.message.getContent().contains("Actual value"));
        org.junit.jupiter.api.Assertions.assertFalse(
                notifications.message.getContent().contains("Monitored server"));
        org.junit.jupiter.api.Assertions.assertFalse(
                notifications.message.getEmailContent().contains("Current state"));
        org.junit.jupiter.api.Assertions.assertTrue(
                notifications.message.getEmailContent().startsWith("<!doctype html>"));
    }

    @Test
    void variableHandlerShouldPublishSummaryAndItemResults() {
        DataCheckContext context = context();
        DataCheckResult result = result(false, false);

        new VariableDataCheckHandler().handle(handlerConfig("VARIABLE", Map.of()), result, context);

        assertEquals(Boolean.FALSE, context.getTaskContext().getVariable("data_check_success"));
        assertEquals(1, context.getTaskContext().getVariable("data_check_failed"));
        assertEquals(
                result.getItems().getFirst(), context.getTaskContext().getVariable("check-one"));
    }

    @Test
    void compositeShouldRejectUnsupportedHandler() {
        CompositeDataCheckHandler handler =
                new CompositeDataCheckHandler(List.of(new VariableDataCheckHandler()));

        assertFalse(handler.supports(handlerConfig("UNKNOWN", Map.of())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        handler.handle(
                                handlerConfig("UNKNOWN", Map.of()), result(true, true), context()));
    }

    private HandlerConfig notifyConfig(boolean notifyOnSuccess) {
        return handlerConfig(
                "NOTIFY", Map.of("notifyConfigId", 51L, "notifyOnSuccess", notifyOnSuccess));
    }

    private HandlerConfig handlerConfig(String type, Map<String, Object> params) {
        HandlerConfig config = new HandlerConfig();
        config.setType(type);
        config.setParams(params);
        return config;
    }

    private DataCheckResult result(boolean taskSuccess, boolean itemSuccess) {
        DataCheckItemResult item =
                DataCheckItemResult.builder()
                        .name("check-one")
                        .success(itemSuccess)
                        .actual(1)
                        .expected(1)
                        .level(itemSuccess ? CheckLevel.ERROR : CheckLevel.WARN)
                        .build();
        return DataCheckResult.from(List.of(item), taskSuccess);
    }

    private DataCheckContext context() {
        ExecutionHandle handle = new ExecutionHandle(41L);
        TaskContext taskContext =
                new TaskContext(41L, handle, null, new RecordingInstanceLogs(), null);
        return DataCheckContext.builder().instanceId(41L).taskContext(taskContext).build();
    }

    private static final class RecordingNotificationService extends NotificationService {
        private int calls;
        private Long configId;
        private NotifyMessage message;
        private NotifyDispatchContext context;

        private RecordingNotificationService() {
            super(null, null, null);
        }

        @Override
        public NotifyDispatchResult send(
                Long notifyConfigId,
                NotifyMessage notifyMessage,
                NotifyDispatchContext dispatchContext) {
            calls++;
            configId = notifyConfigId;
            message = notifyMessage;
            context = dispatchContext;
            return new NotifyDispatchResult(NotifyDispatchStatus.DELIVERED, null);
        }
    }
}
