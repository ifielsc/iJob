package com.ifelio.ijob.core.notify.channel.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.contract.enums.NotifyType;
import com.ifelio.ijob.core.notify.channel.NotifyChannel;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class WebhookChannels {
    @Bean
    public NotifyChannel wecomChannel(
            ObjectMapper json, WebhookPayloads payloads, WebhookTransport transport) {
        return new WebhookNotifyChannel(NotifyType.WECOM, json, payloads, transport);
    }

    @Bean
    public NotifyChannel feishuChannel(
            ObjectMapper json, WebhookPayloads payloads, WebhookTransport transport) {
        return new WebhookNotifyChannel(NotifyType.FEISHU, json, payloads, transport);
    }

    @Bean
    public NotifyChannel slackChannel(
            ObjectMapper json, WebhookPayloads payloads, WebhookTransport transport) {
        return new WebhookNotifyChannel(NotifyType.SLACK, json, payloads, transport);
    }

    @Bean
    public NotifyChannel discordChannel(
            ObjectMapper json, WebhookPayloads payloads, WebhookTransport transport) {
        return new WebhookNotifyChannel(NotifyType.DISCORD, json, payloads, transport);
    }
}
