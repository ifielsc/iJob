package com.ifelio.ijob.core.notify.channel.dingtalk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.contract.enums.NotifyType;
import com.ifelio.ijob.core.notify.NotifyAuditFailure;
import com.ifelio.ijob.core.notify.NotifyMessage;
import com.ifelio.ijob.core.notify.channel.NotifyChannel;
import com.ifelio.ijob.core.notify.channel.NotifyChannelResult;
import com.ifelio.ijob.core.notify.channel.NotifyDeliveryException;
import com.ifelio.ijob.infra.entity.notify.NotifyConfig;

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
