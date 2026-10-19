package com.ifielsc.ijob.core.notify.channel.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.enums.NotifyType;
import com.ifielsc.ijob.contract.notify.NotifyConfigRequest;
import com.ifielsc.ijob.core.notify.NotifyAuditFailure;
import com.ifielsc.ijob.core.notify.NotifyMessage;
import com.ifielsc.ijob.core.notify.channel.NotifyDeliveryException;
import com.ifielsc.ijob.infra.entity.notify.NotifyConfig;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

class WebhookChannelsTest {
    private final ObjectMapper json = new ObjectMapper();
    private final WebhookPayloads payloads = new WebhookPayloads(json);

    @Test
    void endpointsRejectProxyRedirectParametersAndMaskPathSecrets() {
        String legacy = "https://relay.example.test:8443/hook?token=old";
        assertEquals(URI.create(legacy), WebhookEndpoints.require(NotifyType.DINGTALK, legacy));
        assertEquals("url:" + legacy, WebhookEndpoints.identity(NotifyType.DINGTALK, legacy));
        assertEquals(
                "same",
                WebhookEndpoints.identity(
                        NotifyType.DINGTALK,
                        "https://oapi.dingtalk.com/robot/send?access_token=same&legacy=value"));
        for (String url :
                List.of(
                        "http://hooks.slack.com/services/T1/B1/token",
                        "https://hooks.slack.com.evil.invalid/services/T1/B1/token",
                        "https://user@hooks.slack.com/services/T1/B1/token",
                        "https://hooks.slack.com/services/T1/B1/token?redirect=secret",
                        "https://127.0.0.1/services/T1/B1/token",
                        "https://hooks.slack.com/services/T1/../token"))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> WebhookEndpoints.require(NotifyType.SLACK, url));
        assertFalse(
                WebhookEndpoints.display(
                                NotifyType.SLACK,
                                "https://hooks.slack.com/services/T1/B1/secretCanary")
                        .contains("secretCanary"));
        assertEquals(
                "123",
                WebhookEndpoints.identity(
                        NotifyType.DISCORD, "https://discord.com/api/v10/webhooks/123/token"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        WebhookEndpoints.require(
                                NotifyType.WECOM,
                                "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=one&key=two"));
    }

    @Test
    void optionalSecretsPreserveReplaceClearAndNeverReturn() {
        var old = config(NotifyType.FEISHU);
        old.setSecret("secretCanary");
        var request = new NotifyConfigRequest.Webhook();
        request.setPreserveExisting(true);
        var preserved = WebhookConfigValues.build(NotifyType.FEISHU, request, old);
        assertEquals("secretCanary", preserved.getSecret());
        request.setClearSecret(true);
        assertEquals(null, WebhookConfigValues.build(NotifyType.FEISHU, request, old).getSecret());
        request.setSecret("replacement");
        assertThrows(
                RuntimeException.class,
                () -> WebhookConfigValues.build(NotifyType.FEISHU, request, old));
        request.setClearSecret(false);
        assertEquals(
                "replacement",
                WebhookConfigValues.build(NotifyType.FEISHU, request, old).getSecret());
        assertFalse(
                WebhookConfigValues.view(NotifyType.FEISHU, old)
                        .toString()
                        .contains("secretCanary"));
    }

    @Test
    void renderingIsFrozenBoundedAndMentionsCannotBeInjected() throws Exception {
        var message =
                NotifyMessage.builder()
                        .subject("Alert")
                        .content(
                                "**订单**\n<@everyone> <!channel>\n"
                                        + "检查😀".repeat(3000)
                                        + "\nTask #36101 · Instance #46101")
                        .emailContent("<p>unchanged</p>")
                        .build();
        for (var type :
                List.of(
                        NotifyType.WECOM,
                        NotifyType.FEISHU,
                        NotifyType.SLACK,
                        NotifyType.DISCORD)) {
            var c = config(type);
            var frozen = payloads.freeze(type, json.writeValueAsString(c), message);
            assertEquals(1, frozen.getContentVersion());
            assertTrue(frozen.getContent().contains("46101"));
            assertSame(frozen, payloads.freeze(type, "changed-config", frozen));
            var body = json.readTree(frozen.getContent());
            if (type == NotifyType.WECOM)
                assertTrue(
                        body.path("markdown")
                                        .path("content")
                                        .asText()
                                        .getBytes(StandardCharsets.UTF_8)
                                        .length
                                <= 4096);
            if (type == NotifyType.SLACK)
                assertEquals(
                        "plain_text",
                        body.path("blocks").get(1).path("text").path("type").asText());
            if (type == NotifyType.DISCORD)
                assertEquals(0, body.path("allowed_mentions").path("parse").size());
        }
        assertSame(message, payloads.freeze(NotifyType.EMAIL, "", message));
        assertSame(message, payloads.freeze(NotifyType.DINGTALK, "", message));
    }

    @Test
    void businessErrorsRetryAfterAndNoContentLeak() {
        try (var transport = new ClosingTransport(json)) {
            transport.classify(NotifyType.SLACK, 200, null, null, null, "ok");
            transport.classify(NotifyType.DISCORD, 200, null, null, null, "{\"id\":\"123\"}");
            transport.classify(NotifyType.FEISHU, 200, null, null, null, "{\"code\":0}");
            transport.classify(NotifyType.WECOM, 200, null, null, null, "{\"errcode\":0}");
            var limited =
                    assertThrows(
                            NotifyDeliveryException.class,
                            () ->
                                    transport.classify(
                                            NotifyType.DISCORD,
                                            429,
                                            "2",
                                            null,
                                            null,
                                            "{\"retry_after\":2.5,\"global\":true,\"message\":\"secretCanary\"}"));
            assertEquals(2500, limited.retryAfterMillis());
            assertTrue(limited.global());
            assertEquals(NotifyAuditFailure.RATE_LIMITED, limited.failure());
            assertFalse(limited.toString().contains("secretCanary"));
            for (var type : List.of(NotifyType.FEISHU, NotifyType.WECOM, NotifyType.DISCORD))
                assertThrows(
                        NotifyDeliveryException.class,
                        () -> transport.classify(type, 200, null, null, null, "{}"));
            var terminal =
                    assertThrows(
                            NotifyDeliveryException.class,
                            () ->
                                    transport.classify(
                                            NotifyType.DISCORD,
                                            404,
                                            null,
                                            null,
                                            null,
                                            "secretCanary"));
            assertFalse(terminal.failure().isRetryable());
            assertThrows(
                    NotifyDeliveryException.class,
                    () ->
                            transport.classify(
                                    NotifyType.FEISHU, 200, null, null, null, "{\"code\":19024}"));
        }
    }

    @Test
    void feishuSignsAtSendAndDiscordWaitsForReceipt() throws Exception {
        try (var transport = new ClosingTransport(json)) {
            for (var type : List.of(NotifyType.FEISHU, NotifyType.DISCORD)) {
                var c = config(type);
                if (type == NotifyType.FEISHU) c.setSecret("secretCanary");
                var record = new NotifyConfig();
                record.setType(type);
                record.setConfig(json.writeValueAsString(c));
                var frozen =
                        payloads.freeze(
                                type,
                                record.getConfig(),
                                NotifyMessage.builder().subject("test").content("test").build());
                assertFalse(frozen.getContent().contains("sign"));
                new WebhookNotifyChannel(type, json, payloads, transport).send(record, frozen);
                if (type == NotifyType.DISCORD)
                    assertEquals("wait=true", transport.endpoint.getQuery());
                else {
                    var body = json.readTree(transport.sent);
                    assertTrue(body.has("sign"));
                    assertTrue(body.has("timestamp"));
                    assertFalse(transport.sent.contains("secretCanary"));
                    assertNotEquals(frozen.getContent(), transport.sent);
                }
            }
        }
    }

    @Test
    void dingTalkProductionPathKeepsMarkdownAndSignsEachRequest() throws Exception {
        try (var transport = new ClosingTransport(json)) {
            var config = new com.ifielsc.ijob.core.notify.channel.dingtalk.DingTalkConfig();
            config.setWebhook("https://oapi.dingtalk.com/robot/send?access_token=token");
            config.setSecret("secretCanary");
            config.setAtAll(true);
            config.setAtMobiles(List.of("13800000000"));
            new com.ifielsc.ijob.core.notify.channel.dingtalk.DingTalkClient(transport, json)
                    .send(
                            config,
                            NotifyMessage.builder()
                                    .subject("subject")
                                    .content("**unchanged**")
                                    .build());
            assertTrue(transport.endpoint.getQuery().contains("timestamp="));
            assertTrue(transport.endpoint.getQuery().contains("sign="));
            assertFalse(transport.endpoint.toString().contains("secretCanary"));
            var body = json.readTree(transport.sent);
            assertEquals("**unchanged**", body.path("markdown").path("text").asText());
            assertTrue(body.path("at").path("isAtAll").asBoolean());
            assertEquals("13800000000", body.path("at").path("atMobiles").get(0).asText());
        }
    }

    private WebhookConfig config(NotifyType type) {
        var c = new WebhookConfig();
        c.setFormat("Markdown");
        c.setWebhook(
                Map.of(
                                NotifyType.WECOM,
                                "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=token",
                                NotifyType.FEISHU,
                                "https://open.feishu.cn/open-apis/bot/v2/hook/token",
                                NotifyType.SLACK,
                                "https://hooks.slack.com/services/T1/B1/token",
                                NotifyType.DISCORD,
                                "https://discord.com/api/webhooks/123/token")
                        .get(type));
        return c;
    }

    private static class ClosingTransport extends WebhookTransport implements AutoCloseable {
        String sent;
        URI endpoint;

        ClosingTransport(ObjectMapper json) {
            super(json);
        }

        @Override
        public void send(NotifyType type, URI endpoint, String body) {
            this.sent = body;
            this.endpoint = endpoint;
        }
    }
}
