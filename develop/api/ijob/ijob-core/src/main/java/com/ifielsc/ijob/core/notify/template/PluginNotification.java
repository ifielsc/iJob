// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify.template;

import com.ifielsc.ijob.core.notify.NotificationContext;
import com.ifielsc.ijob.core.notify.NotifyMessage;
import com.ifielsc.ijob.core.resource.ResourceService;
import com.ifielsc.ijob.infra.mapper.runtime.InstanceMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Bounded channel-specific plugin notices, frozen before durable delivery. */
@Component
@RequiredArgsConstructor
public class PluginNotification {
    private final NotificationMessages messages;
    private final InstanceMapper instances;
    private final ResourceService resources;

    public record Field(String label, String value) {}

    public record Item(String name, String level, List<Field> fields) {}

    public NotifyMessage build(
            Long instanceId,
            Long resourceId,
            String resourceLabel,
            LocalDateTime occurredAt,
            String timeLabel,
            String title,
            String severity,
            String summary,
            List<Item> items) {
        var instance = instances == null ? null : instances.selectSummaryById(instanceId);
        String taskName =
                instance == null || instance.getTargetName() == null
                        ? messages.text("email.unnamed")
                        : instance.getTargetName();
        var resource =
                resources == null || resourceId == null ? null : resources.getResource(resourceId);
        List<Field> info = new ArrayList<>();
        info.add(new Field(timeLabel, ExecutionEmail.time(messages, occurredAt)));
        info.add(
                new Field(
                        messages.text("email.timezone"),
                        ExecutionEmail.zone(
                                messages,
                                NotificationContext.builder().startTime(occurredAt).build())));
        if (resourceId != null)
            info.add(
                    new Field(
                            resourceLabel,
                            (resource == null ? "—" : resource.getName())
                                    + " (#"
                                    + resourceId
                                    + ")"));
        if (instance != null && instance.getParentInstanceId() != null) {
            var parent = instances.selectSummaryById(instance.getParentInstanceId());
            if (parent != null)
                info.add(
                        new Field(
                                messages.text("plugin.job"),
                                parent.getTargetName() + " (#" + parent.getTargetId() + ")"));
        }
        List<Field> identity =
                List.of(
                        new Field(
                                messages.text("email.targetId", messages.text("email.task")),
                                instance == null ? "—" : String.valueOf(instance.getTargetId())),
                        new Field(messages.text("email.instanceId"), String.valueOf(instanceId)));
        return render(taskName, title, severity, summary, info, identity, items);
    }

    /** Pure rendering used by live plugin notices and sample previews. */
    public NotifyMessage render(
            String taskName,
            String title,
            String severity,
            String summary,
            List<Field> info,
            List<Field> identity,
            List<Item> items) {
        String subject = "[iJob] " + limit(title, 100) + " · " + limit(taskName, 100);
        return NotifyMessage.builder()
                .title(subject)
                .subject(subject)
                .emailSubject(subject)
                .content(markdown(title, taskName, summary, info, identity, items))
                .emailContent(email(title, taskName, severity, summary, info, identity, items))
                .build();
    }

    private String markdown(
            String title,
            String name,
            String summary,
            List<Field> info,
            List<Field> identity,
            List<Item> items) {
        StringBuilder out =
                new StringBuilder("### iJob · ")
                        .append(md(title))
                        .append("\n\n**")
                        .append(md(name))
                        .append("**\n\n")
                        .append(md(summary))
                        .append("\n\n");
        for (Field f : info)
            out.append("- ")
                    .append(md(f.label()))
                    .append(": ")
                    .append(md(f.value()))
                    .append("\n\n");
        for (Item item : items.stream().limit(5).toList()) {
            out.append("---\n\n**")
                    .append(md(item.name()))
                    .append(" · ")
                    .append(md(item.level()))
                    .append("**\n\n");
            for (Field f : item.fields())
                out.append("- ")
                        .append(md(f.label()))
                        .append(": ")
                        .append(md(f.value()))
                        .append("\n\n");
        }
        if (items.size() > 5)
            out.append(md(messages.text("plugin.remaining", items.size() - 5))).append("\n\n");
        out.append("---\n\n");
        for (Field f : identity)
            out.append(md(f.label())).append(": ").append(md(f.value())).append("\n\n");
        return out.toString();
    }

    private String email(
            String title,
            String name,
            String severity,
            String summary,
            List<Field> info,
            List<Field> identity,
            List<Item> items) {
        String accent =
                "success".equals(severity)
                        ? "#15815a"
                        : "warning".equals(severity) ? "#a96909" : "#c93636";
        StringBuilder out =
                new StringBuilder(
                        """
<!doctype html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="margin:0;background:#f3f5f9;color:#243247;font-family:Arial,'Microsoft YaHei',sans-serif">
<table role="presentation" width="100%" cellspacing="0" cellpadding="0"><tr><td align="center" style="padding:24px 12px">
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:640px;background:#fff;border:1px solid #e2e7ef;border-radius:12px;overflow:hidden">
""");
        out.append("<tr><td style=\"height:5px;background:")
                .append(accent)
                .append("\"></td></tr><tr><td style=\"padding:24px\">")
                .append("<div style=\"color:#315fd8;font-size:13px;font-weight:bold\">iJob</div>")
                .append(
                        "<h1 style=\"font-size:21px;line-height:1.5;word-break:break-word;margin:14px"
                            + " 0\">")
                .append(html(name))
                .append("</h1>")
                .append(
                        "<span style=\"display:inline-block;padding:6px"
                            + " 12px;background:#f3f5f9;border-radius:5px;font-size:13px;font-weight:bold;color:")
                .append(accent)
                .append("\">")
                .append(html(title))
                .append("</span>")
                .append("<p style=\"font-size:14px;line-height:1.8\">")
                .append(html(summary))
                .append("</p>")
                .append(
                        "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\""
                                + " cellpadding=\"0\">");
        for (Field f : info) out.append(row(f));
        out.append("</table>");
        for (Item item : items.stream().limit(10).toList()) {
            out.append(
                            "<div style=\"margin-top:20px;padding:12px"
                                    + " 14px;background:#f6f8fb;border-left:3px solid ")
                    .append(accent)
                    .append(";font-size:14px;line-height:1.6;word-break:break-word\"><strong>")
                    .append(html(item.name()))
                    .append("</strong><span style=\"color:")
                    .append(accent)
                    .append("\"> · ")
                    .append(html(item.level()))
                    .append("</span></div>")
                    .append("<table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\">");
            for (Field f : item.fields()) out.append(row(f));
            out.append("</table>");
        }
        if (items.size() > 10)
            out.append("<p>")
                    .append(html(messages.text("plugin.remaining", items.size() - 10)))
                    .append("</p>");
        out.append(
                "</td></tr><tr><td style=\"padding:16px"
                    + " 24px;background:#fafbfc;color:#728095;font-size:12px;line-height:1.8\">");
        for (Field f : identity)
            out.append(html(f.label())).append(": ").append(html(f.value())).append("<br>");
        return out.append("</td></tr></table></td></tr></table></body></html>").toString();
    }

    private String row(Field field) {
        return "<tr><td style=\"width:96px;padding:9px 8px 9px"
                + " 0;vertical-align:top;color:#728095;border-bottom:1px solid"
                + " #edf0f4;font-size:13px\">"
                + html(field.label())
                + "</td><td style=\"padding:9px 0;border-bottom:1px solid"
                + " #edf0f4;font-size:13px;line-height:1.7;word-break:break-word;overflow-wrap:anywhere\">"
                + html(field.value())
                + "</td></tr>";
    }

    private String html(String value) {
        return limit(value, 240)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String md(String value) {
        StringBuilder out = new StringBuilder();
        for (char c : limit(value, 160).toCharArray()) {
            if ("\\`*_{}[]()#!|".indexOf(c) >= 0) out.append('\\');
            if (c == '<') out.append("&lt;");
            else if (c == '>') out.append("&gt;");
            else if (c == '&') out.append("&amp;");
            else out.append(c);
        }
        return out.toString();
    }

    private static String limit(String value, int length) {
        return ExecutionEmail.limit(
                value == null ? "—" : value.replaceAll("[\\r\\n\\t]+", " "), length);
    }
}
