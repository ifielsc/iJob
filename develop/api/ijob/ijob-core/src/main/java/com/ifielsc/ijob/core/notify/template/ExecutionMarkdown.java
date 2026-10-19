// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify.template;

import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.core.notify.NotificationContext;

/** Markdown rendering uses escaped, bounded user text; no raw HTML or runtime parameters. */
final class ExecutionMarkdown {
    private ExecutionMarkdown() {}

    static String content(
            NotificationMessages m, String target, NotificationContext c, String detail) {
        StringBuilder text =
                new StringBuilder("### iJob · ")
                        .append(escape(ExecutionEmail.result(m, c)))
                        .append("\n\n**")
                        .append(escape(ExecutionEmail.limit(ExecutionEmail.name(m, c), 256)))
                        .append("**\n\n")
                        .append(escape(m.text("email.notice", m.text("email." + target))))
                        .append("\n\n");
        row(text, m.text("email.start"), ExecutionEmail.time(m, c.getStartTime()));
        row(text, m.text("email.end"), ExecutionEmail.time(m, c.getEndTime()));
        row(text, m.text("email.duration"), ExecutionEmail.duration(m, c.getDuration()));
        row(text, m.text("email.timezone"), ExecutionEmail.zone(m, c));
        if (detail != null && !detail.isBlank()) {
            boolean failure =
                    c.getStatus() == InstanceStatus.FAILED
                            || c.getStatus() == InstanceStatus.TIMED_OUT;
            text.append("\n**")
                    .append(m.text(failure ? "email.failure" : "email.message"))
                    .append("**\n\n")
                    .append(escape(ExecutionEmail.limit(detail, 500)))
                    .append("\n\n");
        }
        row(
                text,
                m.text("email.targetId", m.text("email." + target)),
                ExecutionEmail.id(c.getTargetId()));
        row(text, m.text("email.instanceId"), ExecutionEmail.id(c.getInstanceId()));
        return text.toString();
    }

    private static void row(StringBuilder text, String label, String value) {
        text.append("- ").append(escape(label)).append(": ").append(escape(value)).append("\n\n");
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (char ch : value.replace('\r', ' ').replace('\n', ' ').toCharArray()) {
            if ("\\`*_{}[]()#!|".indexOf(ch) >= 0) out.append('\\');
            if (ch == '<') out.append("&lt;");
            else if (ch == '>') out.append("&gt;");
            else if (ch == '&') out.append("&amp;");
            else out.append(ch);
        }
        return out.toString();
    }
}
