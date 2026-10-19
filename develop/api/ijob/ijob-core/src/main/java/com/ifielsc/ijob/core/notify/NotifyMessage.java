// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify;

import com.ifielsc.ijob.contract.enums.NotifyType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotifyMessage {
    private String title;
    private String subject;
    private String content;
    private int contentVersion;
    private String emailSubject;
    private String emailContent;

    /** Freeze the channel-specific rendering before it enters the durable outbox. */
    public NotifyMessage forChannel(NotifyType type) {
        if (type != NotifyType.EMAIL || emailContent == null) return this;
        String selectedSubject = emailSubject == null ? subject : emailSubject;
        return NotifyMessage.builder()
                .title(selectedSubject)
                .subject(selectedSubject)
                .content(emailContent)
                .build();
    }
}
