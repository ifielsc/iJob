// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify.template;

import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.core.notify.NotificationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Self-contained email markup: no external assets, scripts, or unescaped runtime text. */
final class ExecutionEmail {
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ExecutionEmail() {}

    static String subject(NotificationMessages m, String target, NotificationContext c) {
        return m.text(
                "email.subject",
                result(m, c),
                m.text("email." + target),
                limit(name(m, c).replaceAll("[\\r\\n\\t]+", " "), 160));
    }

    static String content(
            NotificationMessages m, String target, NotificationContext c, String detail) {
        boolean failure =
                c.getStatus() == InstanceStatus.FAILED || c.getStatus() == InstanceStatus.TIMED_OUT;
        boolean success = c.getStatus() == InstanceStatus.SUCCESS;
        String accent = failure ? "#c93636" : success ? "#15815a" : "#53657c";
        String tint = failure ? "#fff1f0" : success ? "#eaf7f0" : "#eef2f7";
        String rows =
                row(m.text("email.start"), time(m, c.getStartTime()))
                        + row(m.text("email.end"), time(m, c.getEndTime()))
                        + row(m.text("email.duration"), duration(m, c.getDuration()))
                        + row(m.text("email.timezone"), zone(m, c));
        String note =
                detail == null || detail.isBlank()
                        ? ""
                        : "<tr><td style=\"padding:0 28px 24px\"><div style=\"border-left:3px solid"
                                + " "
                                + accent
                                + ";background:"
                                + tint
                                + ";padding:14px"
                                + " 16px;border-radius:4px;word-break:break-word;overflow-wrap:anywhere\"><div"
                                + " style=\"font-weight:600;margin-bottom:8px;color:"
                                + accent
                                + "\">"
                                + html(m.text(failure ? "email.failure" : "email.message"))
                                + "</div><div"
                                + " style=\"font-size:13px;line-height:1.8;white-space:pre-wrap\">"
                                + html(limit(detail, 500))
                                + "</div></div></td></tr>";
        return """
<!doctype html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="margin:0;padding:0;background:#f3f5f9;color:#243247;font-family:Arial,'Microsoft YaHei',sans-serif">
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#f3f5f9"><tr><td align="center" style="padding:28px 12px">
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:640px;background:#ffffff;border:1px solid #e2e7ef;border-radius:12px;overflow:hidden">
"""
                + "<tr><td style=\"height:5px;background:"
                + accent
                + "\"></td></tr><tr><td style=\"padding:24px 28px 16px\"><div"
                + " style=\"font-size:13px;font-weight:bold;color:#315fd8;letter-spacing:1px\">iJob</div><h1"
                + " style=\"font-size:22px;line-height:1.5;margin:14px 0"
                + " 12px;word-break:break-word;overflow-wrap:anywhere\">"
                + html(limit(name(m, c), 256))
                + "</h1><span style=\"display:inline-block;padding:5px"
                + " 12px;border-radius:5px;font-size:13px;font-weight:bold;color:"
                + accent
                + ";background:"
                + tint
                + "\">"
                + html(result(m, c))
                + "</span>"
                + "<span style=\"font-size:13px;color:#728095;margin-left:12px\">"
                + html(m.text("email.notice", m.text("email." + target)))
                + "</span></td></tr><tr><td style=\"padding:4px 28px 24px\"><table"
                + " role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\""
                + " style=\"font-size:14px;line-height:1.7\">"
                + rows
                + "</table></td></tr>"
                + note
                + "<tr><td style=\"padding:16px 28px;background:#fafbfc;border-top:1px solid"
                + " #edf0f4;color:#7b8798;font-size:12px;line-height:1.8;word-break:break-word\">"
                + html(m.text("email.targetId", m.text("email." + target)))
                + ": "
                + html(id(c.getTargetId()))
                + "<br>"
                + html(m.text("email.instanceId"))
                + ": "
                + html(id(c.getInstanceId()))
                + "</td></tr></table><div"
                + " style=\"font-size:12px;line-height:1.8;color:#909bad;padding-top:16px\">"
                + html(m.text("email.footer"))
                + "</div></td></tr></table></body></html>";
    }

    private static String row(String label, String value) {
        return "<tr><td width=\"96\" style=\"padding:10px 12px 10px 0;border-bottom:1px solid"
                + " #edf0f4;color:#728095;vertical-align:top\">"
                + html(label)
                + "</td><td style=\"padding:10px 0;border-bottom:1px solid"
                + " #edf0f4;word-break:break-word;overflow-wrap:anywhere\">"
                + html(value)
                + "</td></tr>";
    }

    static String result(NotificationMessages m, NotificationContext c) {
        return switch (c.getStatus() == null ? InstanceStatus.WAITING : c.getStatus()) {
            case SUCCESS, FAILED, TIMED_OUT, STOPPED, SKIPPED ->
                    m.text("email.result." + c.getStatus().name());
            default -> m.status(c.getStatus());
        };
    }

    static String name(NotificationMessages m, NotificationContext c) {
        return c.getTargetName() == null || c.getTargetName().isBlank()
                ? m.text("email.unnamed")
                : c.getTargetName().trim();
    }

    private static ZonedDateTime local(NotificationMessages m, LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(m.businessZone());
    }

    static String time(NotificationMessages m, LocalDateTime utc) {
        return utc == null ? "—" : TIME.format(local(m, utc));
    }

    static String zone(NotificationMessages m, NotificationContext c) {
        String label =
                m.businessZone().getId().equals("Asia/Shanghai")
                        ? m.text("email.beijing") + " · Asia/Shanghai"
                        : m.businessZone().getId();
        LocalDateTime first = c.getStartTime() == null ? c.getEndTime() : c.getStartTime();
        if (first == null) return label;
        String start = offset(local(m, first));
        String end = c.getEndTime() == null ? start : offset(local(m, c.getEndTime()));
        return label + " · " + start + (start.equals(end) ? "" : " → " + end);
    }

    private static String offset(ZonedDateTime time) {
        return "UTC"
                + (time.getOffset().equals(ZoneOffset.UTC) ? "+00:00" : time.getOffset().getId());
    }

    static String duration(NotificationMessages m, Long millis) {
        if (millis == null || millis < 0) return "—";
        if (millis < 1000) return m.text("email.millis", millis.toString());
        long hours = millis / 3600000, minutes = (millis / 60000) % 60;
        String seconds = BigDecimal.valueOf(millis % 60000, 3).stripTrailingZeros().toPlainString();
        return (hours > 0 ? m.text("email.hours", Long.toString(hours)) + " " : "")
                + (minutes > 0 ? m.text("email.minutes", Long.toString(minutes)) + " " : "")
                + m.text("email.seconds", seconds);
    }

    static String id(Long value) {
        return value == null ? "—" : value.toString();
    }

    static String limit(String value, int length) {
        if (value.length() <= length) return value;
        int end = Character.isHighSurrogate(value.charAt(length - 1)) ? length - 1 : length;
        return value.substring(0, end) + "…";
    }

    private static String html(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
