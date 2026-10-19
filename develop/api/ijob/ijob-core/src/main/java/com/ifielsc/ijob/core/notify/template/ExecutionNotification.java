// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify.template;

import com.ifielsc.ijob.core.notify.NotificationContext;
import com.ifielsc.ijob.core.notify.NotifyMessage;

final class ExecutionNotification {
    private ExecutionNotification() {}

    static NotifyMessage render(
            NotificationMessages messages, String target, NotificationContext context) {
        String subject = ExecutionEmail.subject(messages, target, context);
        String key = context.getMessageKey();
        // Legacy generic status metadata must not hide the original diagnostic detail.
        String detail =
                key == null || key.startsWith("runtime.execution.") && context.getMessage() != null
                        ? context.getMessage()
                        : messages.render(key, context.getMessageArgs(), context.getMessage());
        return NotifyMessage.builder()
                .title(subject)
                .subject(subject)
                .emailSubject(ExecutionEmail.subject(messages, target, context))
                .emailContent(ExecutionEmail.content(messages, target, context, detail))
                .content(ExecutionMarkdown.content(messages, target, context, detail))
                .build();
    }
}
