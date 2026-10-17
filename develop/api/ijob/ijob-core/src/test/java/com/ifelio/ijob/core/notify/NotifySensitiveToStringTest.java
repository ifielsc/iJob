package com.ifelio.ijob.core.notify;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.ifelio.ijob.core.notify.channel.dingtalk.DingTalkConfig;
import com.ifelio.ijob.core.notify.channel.email.EmailConfig;
import com.ifelio.ijob.infra.entity.notify.NotifyConfig;

import org.junit.jupiter.api.Test;

import java.util.List;

class NotifySensitiveToStringTest {
    @Test
    void notifyEntityAndTypedConfigurationsShouldHideSensitiveValues() {
        NotifyConfig entity = new NotifyConfig();
        entity.setConfig("entity-secret-config");

        EmailConfig email = new EmailConfig();
        email.setFrom("mail-secret-user@example.com");
        email.setTo(List.of("to-secret@example.com"));
        email.setCc(List.of("cc-secret@example.com"));

        DingTalkConfig dingTalk = new DingTalkConfig();
        dingTalk.setWebhook("https://secret-webhook.example/token");
        dingTalk.setSecret("ding-secret-signing-key");
        dingTalk.setAtMobiles(List.of("13800000000"));

        assertAll(
                () -> assertHidden(entity, "entity-secret-config"),
                () ->
                        assertHidden(
                                email,
                                "mail-secret-user@example.com",
                                "to-secret@example.com",
                                "cc-secret@example.com"),
                () ->
                        assertHidden(
                                dingTalk,
                                "https://secret-webhook.example/token",
                                "ding-secret-signing-key",
                                "13800000000"));
    }

    private void assertHidden(Object value, String... secrets) {
        String rendered = value.toString();
        for (String secret : secrets) {
            assertFalse(
                    rendered.contains(secret),
                    () -> "sensitive value was rendered by " + value.getClass().getSimpleName());
        }
    }
}
