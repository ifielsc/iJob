// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.notify;

import com.ifielsc.ijob.contract.enums.NotifyType;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(exclude = {"emailConfig", "dingTalkConfig", "webhookConfig"})
public class NotifyConfigUpsertRequest {
    private String name;
    private NotifyType type;
    private Boolean enabled;
    private NotifyConfigRequest.Email emailConfig;
    private NotifyConfigRequest.DingTalk dingTalkConfig;
    private NotifyConfigRequest.Webhook webhookConfig;
}
