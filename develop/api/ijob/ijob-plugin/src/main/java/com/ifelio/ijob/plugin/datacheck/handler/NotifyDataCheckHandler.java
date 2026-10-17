package com.ifelio.ijob.plugin.datacheck.handler;

import com.ifelio.ijob.contract.enums.NotifySendSource;
import com.ifelio.ijob.core.notify.NotificationService;
import com.ifelio.ijob.core.notify.NotifyDispatchContext;
import com.ifelio.ijob.core.notify.NotifyMessage;
import com.ifelio.ijob.core.notify.template.NotificationMessages;
import com.ifelio.ijob.core.notify.template.PluginNotification;
import com.ifelio.ijob.plugin.datacheck.config.CheckLevel;
import com.ifelio.ijob.plugin.datacheck.config.HandlerConfig;
import com.ifelio.ijob.plugin.datacheck.model.DataCheckContext;
import com.ifelio.ijob.plugin.datacheck.model.DataCheckResult;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotifyDataCheckHandler implements DataCheckHandler {
    private static final String TYPE = "NOTIFY";

    private final NotificationService notificationService;
    private final NotificationMessages messages;
    private final PluginNotification template;

    @Override
    public boolean supports(HandlerConfig config) {
        return TYPE.equalsIgnoreCase(config.getType());
    }

    @Override
    public void handle(HandlerConfig config, DataCheckResult result, DataCheckContext context) {
        Map<String, Object> params = config.getParams();
        boolean notifyOnSuccess = Boolean.TRUE.equals(params.get("notifyOnSuccess"));
        if (result.getFailedCount() == 0 && !notifyOnSuccess) {
            return;
        }
        Long notifyConfigId = ((Number) params.get("notifyConfigId")).longValue();
        notificationService.send(
                notifyConfigId,
                buildMessage(result, context),
                NotifyDispatchContext.builder()
                        .sourceType(NotifySendSource.DATA_CHECK)
                        .instanceId(context.getInstanceId())
                        .build());
    }

    private NotifyMessage buildMessage(DataCheckResult result, DataCheckContext context) {
        boolean failed = result.getFailedCount() > 0;
        var items =
                result.getItems().stream()
                        .filter(item -> !item.isSuccess())
                        .sorted(
                                Comparator.comparingInt(
                                        item -> item.getLevel() == CheckLevel.ERROR ? 0 : 1))
                        .map(
                                item -> {
                                    var config =
                                            context.getChecks() == null
                                                    ? null
                                                    : context.getChecks().stream()
                                                            .filter(
                                                                    check ->
                                                                            check.getName()
                                                                                    .equals(
                                                                                            item
                                                                                                    .getName()))
                                                            .findFirst()
                                                            .orElse(null);
                                    String compare =
                                            config == null || config.getCompareType() == null
                                                    ? "EQ"
                                                    : config.getCompareType().name();
                                    return new PluginNotification.Item(
                                            item.getName(),
                                            messages.text("plugin.level." + item.getLevel()),
                                            List.of(
                                                    new PluginNotification.Field(
                                                            messages.text("plugin.actual"),
                                                            String.valueOf(item.getActual())),
                                                    new PluginNotification.Field(
                                                            messages.text("plugin.expected"),
                                                            messages.text(
                                                                            "plugin.compare."
                                                                                    + compare)
                                                                    + " "
                                                                    + String.valueOf(
                                                                            item.getExpected()))));
                                })
                        .toList();
        return template.build(
                context.getInstanceId(),
                context.getResourceId(),
                messages.text("plugin.dataSource"),
                LocalDateTime.now(ZoneOffset.UTC),
                messages.text("plugin.checkedAt"),
                messages.text(failed ? "plugin.check.failed" : "plugin.check.pass"),
                !failed ? "success" : result.isSuccess() ? "warning" : "error",
                messages.text(
                        "plugin.check.summary",
                        result.getItems().size(),
                        result.getItems().size() - result.getFailedCount(),
                        result.getFailedCount()),
                items);
    }
}
