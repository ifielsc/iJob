package com.ifelio.ijob.core.notify.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.core.notify.NotificationContext;

import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

class NotificationMessagesTest {
    @Test
    void markdownUsesBusinessTimeAndEscapesUntrustedText() {
        var properties = new AppProperties();
        properties.getNotification().setLocale(AppProperties.NotificationLocale.ZH);
        properties.getSchedule().setZoneId("Asia/Shanghai");
        var context =
                NotificationContext.builder()
                        .targetName("[name](https://example.test)")
                        .instanceId(123L)
                        .targetId(7L)
                        .status(InstanceStatus.TIMED_OUT)
                        .startTime(java.time.LocalDateTime.of(2026, 10, 9, 13, 35, 9))
                        .endTime(java.time.LocalDateTime.of(2026, 10, 9, 13, 37, 9))
                        .duration(120000L)
                        .message("<script>" + "x".repeat(5000))
                        .build();
        var message = new TaskNotifyTemplate(new NotificationMessages(properties)).build(context);
        assertTrue(message.getContent().contains("2026-10-09 21:35:09"));
        assertTrue(message.getContent().contains("执行超时"));
        assertFalse(message.getContent().contains("<script>"));
        assertFalse(message.getContent().contains("[name](https://example.test)"));
        assertTrue(message.getContent().length() < 2000);
        assertEquals(message.getEmailSubject(), message.getSubject());
    }

    @Test
    void auditedBusinessFailureUsesConfiguredLanguageInsteadOfStoredDiagnostic() {
        var context =
                NotificationContext.builder()
                        .instanceId(7L)
                        .status(InstanceStatus.FAILED)
                        .messageKey("error.detail.missing-variable")
                        .messageArgs(new Object[] {"inputId"})
                        .message("private driver diagnostic")
                        .build();
        var properties = new AppProperties();
        var en =
                new TaskNotifyTemplate(new NotificationMessages(properties))
                        .build(context)
                        .getContent();
        properties.getNotification().setLocale(AppProperties.NotificationLocale.ZH);
        var zh =
                new TaskNotifyTemplate(new NotificationMessages(properties))
                        .build(context)
                        .getContent();
        assertTrue(en.contains("inputId"));
        assertTrue(zh.contains("inputId"));
        assertNotEquals(en, zh);
        assertFalse(en.contains("private driver diagnostic"));
        assertFalse(zh.contains("private driver diagnostic"));
    }

    @Test
    void configuredLocaleDoesNotFollowRequestLocale() {
        Locale previous = LocaleContextHolder.getLocale();
        try {
            LocaleContextHolder.setLocale(Locale.CHINESE);
            NotificationContext context =
                    NotificationContext.builder()
                            .instanceId(12345L)
                            .targetName("nightly")
                            .status(InstanceStatus.FAILED)
                            .message("driver diagnostic")
                            .build();
            var english = new NotificationMessages(new AppProperties());
            var en = new JobNotifyTemplate(english).build(context);
            assertEquals("[iJob | Failed] Job: nightly", en.getSubject());
            assertTrue(en.getContent().contains("12345"));
            assertTrue(en.getContent().contains("driver diagnostic"));
            AppProperties properties = new AppProperties();
            properties.getNotification().setLocale(AppProperties.NotificationLocale.ZH);
            var chinese = new NotificationMessages(properties);
            assertEquals(
                    "【iJob｜执行失败】任务：nightly",
                    new TaskNotifyTemplate(chinese).build(context).getSubject());
            for (InstanceStatus status : InstanceStatus.values()) {
                assertFalse(english.status(status).isBlank());
                assertFalse(chinese.status(status).isBlank());
            }
        } finally {
            LocaleContextHolder.setLocale(previous);
        }
    }

    @Test
    void unknownHistoricalKeyFallsBackToStoredMessage() {
        NotificationContext context =
                NotificationContext.builder()
                        .status(InstanceStatus.FAILED)
                        .messageKey("retired.unknown.key")
                        .message("historical detail")
                        .build();
        assertTrue(
                new TaskNotifyTemplate(new NotificationMessages(new AppProperties()))
                        .build(context)
                        .getContent()
                        .contains("historical detail"));
    }
}
