// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify.channel.dingtalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.ifielsc.ijob.core.execute.TimeoutExceptionClassifier;
import com.ifielsc.ijob.core.notify.NotifyAuditFailure;
import com.ifielsc.ijob.core.notify.NotifyMessage;
import com.ifielsc.ijob.core.notify.channel.NotifyDeliveryException;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Component
public class DingTalkClient {
    private final RestTemplate restTemplate;
    private final com.ifielsc.ijob.core.notify.channel.webhook.WebhookTransport transport;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public DingTalkClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.transport = null;
        this.json = null;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DingTalkClient(
            com.ifielsc.ijob.core.notify.channel.webhook.WebhookTransport transport,
            com.fasterxml.jackson.databind.ObjectMapper json) {
        this.restTemplate = null;
        this.transport = transport;
        this.json = json;
    }

    public void send(DingTalkConfig config, NotifyMessage message) {
        if (transport != null) {
            String payload;
            java.net.URI endpoint;
            try {
                com.ifielsc.ijob.core.notify.channel.webhook.WebhookEndpoints.require(
                        com.ifielsc.ijob.contract.enums.NotifyType.DINGTALK, config.getWebhook());
                endpoint = java.net.URI.create(buildUrl(config));
                payload = json.writeValueAsString(buildBody(config, message));
            } catch (Exception ignored) {
                throw new NotifyDeliveryException(NotifyAuditFailure.CONFIG_INVALID, null);
            }
            transport.send(com.ifielsc.ijob.contract.enums.NotifyType.DINGTALK, endpoint, payload);
            return;
        }
        String url = buildUrl(config);
        Map<String, Object> body = buildBody(config, message);
        JsonNode response;
        try {
            response = restTemplate.postForObject(url, body, JsonNode.class);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 429)
                throw new NotifyDeliveryException(
                        NotifyAuditFailure.RATE_LIMITED,
                        status,
                        Math.max(
                                60000,
                                com.ifielsc.ijob.core.notify.channel.webhook.WebhookTransport
                                        .retryMillis(
                                                exception.getResponseHeaders() == null
                                                        ? null
                                                        : exception
                                                                .getResponseHeaders()
                                                                .getFirst("Retry-After"))),
                        false);
            throw new NotifyDeliveryException(
                    status == 408 || status >= 500
                            ? NotifyAuditFailure.REMOTE_UNAVAILABLE
                            : NotifyAuditFailure.REMOTE_REJECTED,
                    exception);
        } catch (ResourceAccessException exception) {
            throw new NotifyDeliveryException(
                    TimeoutExceptionClassifier.isTimeout(exception)
                            ? NotifyAuditFailure.DELIVERY_TIMEOUT
                            : NotifyAuditFailure.TRANSPORT_FAILED,
                    exception);
        } catch (RestClientException exception) {
            throw new NotifyDeliveryException(NotifyAuditFailure.RESPONSE_INVALID, exception);
        }
        JsonNode code = response == null ? null : response.get("errcode");
        if (code == null || !code.isIntegralNumber()) {
            throw new NotifyDeliveryException(NotifyAuditFailure.RESPONSE_INVALID, null);
        }
        if (code.bigIntegerValue().signum() != 0) {
            // Never retain provider text: it may contain webhook credentials or message content.
            // Only known configuration/security refusals are terminal. Unknown codes retain
            // bounded outbox retries so new transient provider errors do not silently drop alerts.
            String businessCode = code.bigIntegerValue().toString();
            NotifyAuditFailure failure =
                    switch (businessCode) {
                        case "310000", "40035" -> NotifyAuditFailure.REMOTE_REJECTED;
                        case "130101" -> NotifyAuditFailure.RATE_LIMITED;
                        default -> NotifyAuditFailure.REMOTE_FAILURE;
                    };
            throw new NotifyDeliveryException(
                    failure, 200, failure == NotifyAuditFailure.RATE_LIMITED ? 60000 : 0, false);
        }
    }

    private String buildUrl(DingTalkConfig config) {
        if (StringUtils.hasText(config.getSecret())) {
            long timestamp = System.currentTimeMillis();
            String sign = sign(timestamp, config.getSecret());
            return config.getWebhook() + "&timestamp=" + timestamp + "&sign=" + sign;
        }
        return config.getWebhook();
    }

    private String sign(long timestamp, String secret) {
        try {
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return URLEncoder.encode(
                    Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new NotifyDeliveryException(NotifyAuditFailure.CONFIG_INVALID, e);
        }
    }

    public static Map<String, Object> buildBody(DingTalkConfig config, NotifyMessage message) {
        Map<String, Object> result = new HashMap<>();
        result.put("msgtype", "markdown");

        Map<String, Object> markdown = new HashMap<>();
        markdown.put("title", message.getSubject());
        markdown.put("text", message.getContent());
        result.put("markdown", markdown);

        Map<String, Object> at = new HashMap<>();
        at.put("isAtAll", Boolean.TRUE.equals(config.getAtAll()));
        at.put("atMobiles", config.getAtMobiles());
        result.put("at", at);

        return result;
    }
}
