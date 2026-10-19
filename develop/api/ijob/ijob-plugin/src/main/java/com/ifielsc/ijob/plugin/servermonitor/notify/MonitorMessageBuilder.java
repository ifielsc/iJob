package com.ifielsc.ijob.plugin.servermonitor.notify;

import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.notify.NotifyMessage;
import com.ifielsc.ijob.core.notify.template.NotificationMessages;
import com.ifielsc.ijob.core.notify.template.PluginNotification;
import com.ifielsc.ijob.plugin.servermonitor.AlarmLevel;
import com.ifielsc.ijob.plugin.servermonitor.MonitorCheckResult;
import com.ifielsc.ijob.plugin.servermonitor.MonitorItemType;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorConfig;
import com.ifielsc.ijob.plugin.servermonitor.rule.Threshold;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;

@Component
@RequiredArgsConstructor
public class MonitorMessageBuilder {
    private final NotificationMessages messages;
    private final PluginNotification template;

    public NotifyMessage build(
            MonitorCheckResult result, MonitorConfig config, TaskContext context) {
        var items =
                result.getItems().stream()
                        .filter(item -> item.getLevel() != AlarmLevel.NORMAL)
                        .sorted(
                                Comparator.comparingInt(
                                        item -> item.getLevel() == AlarmLevel.CRITICAL ? 0 : 1))
                        .map(
                                item -> {
                                    String reason =
                                            item.getMessageKey() == null
                                                    ? item.getMessage()
                                                    : messages.text(
                                                            item.getMessageKey(),
                                                            item.getMessageArgs());
                                    String unit =
                                            item.getThreshold() instanceof Threshold
                                                    ? item.getType() == MonitorItemType.NETWORK
                                                            ? " B/s"
                                                            : item.getType()
                                                                                    == MonitorItemType
                                                                                            .APPLICATION
                                                                            && item.getName()
                                                                                    .endsWith(
                                                                                            " Memory")
                                                                    ? " MiB"
                                                                    : "%"
                                                    : "";
                                    String current =
                                            item.getCurrent() == null
                                                    ? messages.text(
                                                            "monitor.application.not-running"
                                                                            .equals(
                                                                                    item
                                                                                            .getMessageKey())
                                                                    ? "plugin.notRunning"
                                                                    : "plugin.unavailable")
                                                    : number(item.getCurrent()) + unit;
                                    var fields = new ArrayList<PluginNotification.Field>();
                                    fields.add(
                                            new PluginNotification.Field(
                                                    messages.text("plugin.currentState"), current));
                                    if (item.getThreshold() instanceof Threshold threshold) {
                                        Object limit =
                                                item.getLevel() == AlarmLevel.CRITICAL
                                                        ? threshold.getCritical()
                                                        : threshold.getWarning();
                                        fields.add(
                                                new PluginNotification.Field(
                                                        messages.text("plugin.threshold"),
                                                        "≥ " + number(limit) + unit));
                                    } else if (item.getThreshold() != null) {
                                        fields.add(
                                                new PluginNotification.Field(
                                                        messages.text("plugin.expected"),
                                                        "≤ " + number(item.getThreshold())));
                                    }
                                    if (reason != null && !reason.isBlank())
                                        fields.add(
                                                new PluginNotification.Field(
                                                        messages.text("plugin.reason"), reason));
                                    return new PluginNotification.Item(
                                            item.getName(),
                                            messages.text("monitor.level." + item.getLevel()),
                                            fields);
                                })
                        .toList();
        return template.build(
                context.getInstanceId(),
                config.getResourceId(),
                messages.text("plugin.monitorServer"),
                result.getCollectTime(),
                messages.text("plugin.collectedAt"),
                messages.text(
                        result.hasCritical()
                                ? "plugin.monitor.critical"
                                : "plugin.monitor.warning"),
                result.hasCritical() ? "error" : "warning",
                messages.text(
                        "plugin.monitor.summary",
                        result.count(AlarmLevel.CRITICAL),
                        result.count(AlarmLevel.WARNING)),
                items);
    }

    private String number(Object value) {
        if (value instanceof Number)
            return new BigDecimal(value.toString())
                    .setScale(2, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString();
        return String.valueOf(value);
    }
}
