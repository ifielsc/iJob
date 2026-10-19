// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify.template;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.NotifyType;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.contract.notify.NotifyConfigRequest;
import com.ifielsc.ijob.contract.notify.NotifyPreviewDTO;
import com.ifielsc.ijob.contract.notify.NotifyPreviewRequest;
import com.ifielsc.ijob.core.notify.NotificationContext;
import com.ifielsc.ijob.core.notify.NotifyMessage;
import com.ifielsc.ijob.core.notify.channel.dingtalk.DingTalkClient;
import com.ifielsc.ijob.core.notify.channel.dingtalk.DingTalkConfig;
import com.ifielsc.ijob.core.notify.channel.webhook.WebhookConfigValues;
import com.ifielsc.ijob.core.notify.channel.webhook.WebhookPayloads;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure preview: no repositories, transport, outbox, or quota service. */
@Service
@RequiredArgsConstructor
public class NotificationPreviewService {
    private final NotificationMessages messages;
    private final WebhookPayloads payloads;

    public NotifyPreviewDTO preview(NotifyPreviewRequest request) {
        if (request == null
                || request.type() == null
                || request.source() == null
                || !Set.of("task", "job", "check", "monitor").contains(request.source())) {
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, "Invalid notification preview");
        }
        NotifyMessage message = sample(request.source()).forChannel(request.type());
        Map<String, Object> body;
        if (request.type() == NotifyType.EMAIL) {
            body = Map.of("subject", message.getSubject(), "html", message.getContent());
        } else {
            var options = new NotifyConfigRequest.Webhook();
            options.setFormat(request.type() == NotifyType.DINGTALK ? "text" : request.format());
            options.setMembers(request.members());
            options.setMobiles(request.mobiles());
            options.setRoles(request.roles());
            options.setAtAll(request.atAll());
            options.setMentionMode(request.mentionMode());
            options.setKeyword(request.keyword());
            var config =
                    WebhookConfigValues.presentation(
                            request.type() == NotifyType.DINGTALK
                                    ? NotifyType.WECOM
                                    : request.type(),
                            options,
                            null,
                            false);
            if (request.type() == NotifyType.DINGTALK) {
                var ding = new DingTalkConfig();
                ding.setAtAll(config.isAtAll());
                ding.setAtMobiles(config.getMobiles());
                body = DingTalkClient.buildBody(ding, message);
            } else {
                body = payloads.body(request.type(), config, message);
            }
        }
        return new NotifyPreviewDTO(request.type(), request.source(), message.getSubject(), body);
    }

    NotifyMessage sample(String source) {
        LocalDateTime start = LocalDateTime.of(2026, 10, 17, 2, 24);
        var context =
                NotificationContext.builder()
                        .instanceId(46101L)
                        .targetId(36101L)
                        .targetType("job".equals(source) ? TargetType.JOB : TargetType.TASK)
                        .targetName(messages.text("preview.name." + source))
                        .status(InstanceStatus.SUCCESS)
                        .startTime(start)
                        .endTime(start.plusSeconds(2))
                        .duration(2000L)
                        .retryCount(0)
                        .build();
        if ("task".equals(source)) return new TaskNotifyTemplate(messages).build(context);
        if ("job".equals(source)) return new JobNotifyTemplate(messages).build(context);
        boolean check = "check".equals(source);
        var info =
                List.of(
                        new PluginNotification.Field(
                                messages.text(check ? "plugin.checkedAt" : "plugin.collectedAt"),
                                ExecutionEmail.time(messages, start)),
                        new PluginNotification.Field(
                                messages.text("email.timezone"),
                                ExecutionEmail.zone(messages, context)),
                        new PluginNotification.Field(
                                messages.text(check ? "plugin.dataSource" : "plugin.monitorServer"),
                                check ? "orders · MySQL (#8101)" : "prod-app-02 (#8102)"));
        var identity =
                List.of(
                        new PluginNotification.Field(
                                messages.text("email.targetId", messages.text("email.task")),
                                "36101"),
                        new PluginNotification.Field(messages.text("email.instanceId"), "46101"));
        var item =
                new PluginNotification.Item(
                        messages.text(check ? "preview.check.item" : "preview.monitor.item"),
                        messages.text(check ? "plugin.level.ERROR" : "preview.monitor.level"),
                        check
                                ? List.of(
                                        new PluginNotification.Field(
                                                messages.text("plugin.actual"), "998"),
                                        new PluginNotification.Field(
                                                messages.text("plugin.expected"),
                                                messages.text("plugin.compare.EQ") + " 1000"))
                                : List.of(
                                        new PluginNotification.Field(
                                                messages.text("plugin.currentState"), "92%"),
                                        new PluginNotification.Field(
                                                messages.text("plugin.threshold"), "≥ 90%"),
                                        new PluginNotification.Field(
                                                messages.text("plugin.reason"),
                                                messages.text("preview.monitor.reason"))));
        return new PluginNotification(messages, null, null)
                .render(
                        context.getTargetName(),
                        messages.text(check ? "plugin.check.failed" : "plugin.monitor.critical"),
                        "error",
                        check
                                ? messages.text("plugin.check.summary", 1, 0, 1)
                                : messages.text("plugin.monitor.summary", 1, 0),
                        info,
                        identity,
                        List.of(item));
    }
}
