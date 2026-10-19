// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify.template;

import com.ifielsc.ijob.core.notify.NotificationContext;
import com.ifielsc.ijob.core.notify.NotifyMessage;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobNotifyTemplate implements NotifyTemplate {
    private final NotificationMessages messages;

    @Override
    public NotifyMessage build(NotificationContext context) {
        return ExecutionNotification.render(messages, "job", context);
    }
}
