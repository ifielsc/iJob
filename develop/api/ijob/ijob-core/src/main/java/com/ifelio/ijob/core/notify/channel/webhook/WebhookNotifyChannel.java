package com.ifelio.ijob.core.notify.channel.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ifelio.ijob.contract.enums.NotifyType;
import com.ifelio.ijob.core.notify.NotifyAuditFailure;
import com.ifelio.ijob.core.notify.NotifyMessage;
import com.ifelio.ijob.core.notify.channel.NotifyChannel;
import com.ifelio.ijob.core.notify.channel.NotifyChannelResult;
import com.ifelio.ijob.core.notify.channel.NotifyDeliveryException;
import com.ifelio.ijob.infra.entity.notify.NotifyConfig;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class WebhookNotifyChannel implements NotifyChannel {
    private final NotifyType type;
    private final ObjectMapper json;
    private final WebhookPayloads payloads;
    private final WebhookTransport transport;

    public WebhookNotifyChannel(
            NotifyType type,
            ObjectMapper json,
            WebhookPayloads payloads,
            WebhookTransport transport) {
        this.type = type;
        this.json = json;
        this.payloads = payloads;
        this.transport = transport;
    }

    @Override
    public NotifyType type() {
        return type;
    }

    @Override
    public NotifyChannelResult send(NotifyConfig config, NotifyMessage message) {
        URI endpoint;
        String body;
        try {
            var c = json.readValue(config.getConfig(), WebhookConfig.class);
            endpoint = WebhookEndpoints.require(type, c.getWebhook());
            NotifyMessage frozen = payloads.freeze(type, config.getConfig(), message);
            ObjectNode payload = (ObjectNode) json.readTree(frozen.getContent());
            if (type == NotifyType.FEISHU && c.getSecret() != null) {
                String timestamp = Long.toString(System.currentTimeMillis() / 1000);
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(
                        new SecretKeySpec(
                                (timestamp + "\n" + c.getSecret()).getBytes(StandardCharsets.UTF_8),
                                "HmacSHA256"));
                payload.put("timestamp", timestamp);
                payload.put("sign", Base64.getEncoder().encodeToString(mac.doFinal(new byte[0])));
            }
            if (type == NotifyType.DISCORD) endpoint = URI.create(endpoint + "?wait=true");
            body = json.writeValueAsString(payload);
            if (body.getBytes(StandardCharsets.UTF_8).length
                    > (type == NotifyType.FEISHU ? 20000 : 64000))
                throw new IllegalArgumentException();
        } catch (Exception ignored) {
            throw new NotifyDeliveryException(NotifyAuditFailure.CONFIG_INVALID, null);
        }
        transport.send(type, endpoint, body);
        return NotifyChannelResult.DELIVERED;
    }
}
