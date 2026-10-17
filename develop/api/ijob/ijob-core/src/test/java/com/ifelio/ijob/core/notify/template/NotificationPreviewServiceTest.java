package com.ifelio.ijob.core.notify.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.contract.enums.NotifyType;
import com.ifelio.ijob.contract.notify.NotifyPreviewRequest;
import com.ifelio.ijob.core.notify.channel.webhook.WebhookConfig;
import com.ifelio.ijob.core.notify.channel.webhook.WebhookPayloads;

import org.junit.jupiter.api.Test;

import java.util.List;

class NotificationPreviewServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final NotificationMessages messages = new NotificationMessages(new AppProperties());
    private final WebhookPayloads payloads = new WebhookPayloads(json);
    private final NotificationPreviewService service =
            new NotificationPreviewService(messages, payloads);

    private NotifyPreviewRequest request(NotifyType type, String source) {
        return new NotifyPreviewRequest(
                type, source, "Markdown", List.of(), List.of(), List.of(), false, "none", null);
    }

    @Test
    void everySourceUsesTheSameFrozenBodyAsDeliveryForEveryWebhook() throws Exception {
        for (String source : List.of("task", "job", "check", "monitor")) {
            for (NotifyType type :
                    List.of(
                            NotifyType.WECOM,
                            NotifyType.FEISHU,
                            NotifyType.SLACK,
                            NotifyType.DISCORD)) {
                WebhookConfig config = new WebhookConfig();
                config.setFormat("Markdown");
                var frozen =
                        payloads.freeze(
                                type, json.writeValueAsString(config), service.sample(source));
                assertEquals(
                        json.readTree(frozen.getContent()),
                        json.valueToTree(service.preview(request(type, source)).payload()),
                        type + ":" + source);
            }
        }
    }

    @Test
    void emailAndDingTalkUseRealChannelContentWithIndependentSourceLayouts() {
        for (String source : List.of("task", "job", "check", "monitor")) {
            var message = service.sample(source);
            var mail = service.preview(request(NotifyType.EMAIL, source));
            assertEquals(
                    message.forChannel(NotifyType.EMAIL).getContent(), mail.payload().get("html"));
            assertEquals(message.forChannel(NotifyType.EMAIL).getSubject(), mail.subject());
            var ding =
                    json.valueToTree(
                            service.preview(request(NotifyType.DINGTALK, source)).payload());
            assertEquals(message.getContent(), ding.path("markdown").path("text").asText());
        }
        String task = service.sample("task").getContent();
        assertTrue(task.contains(messages.text("email.end")));
        assertTrue(task.contains("UTC+08:00"));
        assertFalse(service.sample("check").getContent().contains(messages.text("email.end")));
        assertTrue(service.sample("check").getContent().contains("998"));
        assertTrue(service.sample("monitor").getContent().contains("92%"));
        assertTrue(service.sample("job").getContent().contains(messages.text("email.job")));
    }

    @Test
    void draftFormatMentionsAndKeywordAreAppliedByTheDeliveryRenderer() {
        var wecom =
                service.preview(
                        new NotifyPreviewRequest(
                                NotifyType.WECOM,
                                "task",
                                "text",
                                List.of("alice"),
                                List.of("13800138000"),
                                List.of(),
                                true,
                                "none",
                                null));
        var body = json.valueToTree(wecom.payload());
        assertEquals("text", body.path("msgtype").asText());
        assertTrue(body.path("text").path("mentioned_list").toString().contains("@all"));
        assertFalse(body.path("text").path("content").asText().startsWith("###"));
        var feishu =
                service.preview(
                        new NotifyPreviewRequest(
                                NotifyType.FEISHU,
                                "check",
                                null,
                                List.of("ou_123"),
                                null,
                                null,
                                false,
                                "none",
                                "Operations"));
        body = json.valueToTree(feishu.payload());
        assertTrue(
                body.path("card")
                        .path("header")
                        .path("title")
                        .path("content")
                        .asText()
                        .startsWith("Operations · "));
        assertTrue(body.toString().contains("<at id=ou_123></at>"));
        assertFalse(body.toString().contains("webhook"));
        assertFalse(body.toString().contains("secret"));
    }

    @Test
    void previewRejectsInvalidSourceAndPresentationOptionsBeforeRendering() {
        assertThrows(
                BusinessException.class,
                () -> service.preview(request(NotifyType.EMAIL, "invalid")));
        assertThrows(
                BusinessException.class,
                () ->
                        service.preview(
                                new NotifyPreviewRequest(
                                        NotifyType.WECOM,
                                        "check",
                                        "invalid",
                                        null,
                                        null,
                                        null,
                                        false,
                                        null,
                                        null)));
        assertThrows(
                BusinessException.class,
                () ->
                        service.preview(
                                new NotifyPreviewRequest(
                                        NotifyType.SLACK,
                                        "check",
                                        null,
                                        List.of("<script>"),
                                        null,
                                        null,
                                        false,
                                        "members",
                                        null)));
    }
}
