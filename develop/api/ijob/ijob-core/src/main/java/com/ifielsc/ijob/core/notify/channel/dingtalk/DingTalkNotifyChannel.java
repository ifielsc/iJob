// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify.channel.dingtalk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.enums.NotifyType;
import com.ifielsc.ijob.core.notify.NotifyAuditFailure;
import com.ifielsc.ijob.core.notify.NotifyMessage;
import com.ifielsc.ijob.core.notify.channel.NotifyChannel;
import com.ifielsc.ijob.core.notify.channel.NotifyChannelResult;
import com.ifielsc.ijob.core.notify.channel.NotifyDeliveryException;
import com.ifielsc.ijob.infra.entity.notify.NotifyConfig;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DingTalkNotifyChannel implements NotifyChannel {
    private final ObjectMapper objectMapper;
    private final DingTalkClient dingTalkClient;

    @Override
    public NotifyType type() {
        return NotifyType.DINGTALK;
    }

    @Override
    public NotifyChannelResult send(NotifyConfig config, NotifyMessage message) {
        DingTalkConfig dingConfig;
        try {
            dingConfig = objectMapper.readValue(config.getConfig(), DingTalkConfig.class);
            if (dingConfig == null
                    || !org.springframework.util.StringUtils.hasText(dingConfig.getWebhook())) {
                throw new IllegalArgumentException("DingTalk webhook is required");
            }
        } catch (Exception e) {
            throw new NotifyDeliveryException(NotifyAuditFailure.CONFIG_INVALID, e);
        }
        dingTalkClient.send(dingConfig, message);
        return NotifyChannelResult.DELIVERED;
    }
}
