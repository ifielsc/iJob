package com.ifielsc.ijob.core.notify.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.NotifyType;
import com.ifielsc.ijob.core.notify.NotificationContext;
import com.ifielsc.ijob.core.notify.NotifyMessage;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

class ExecutionEmailTest {
    private NotificationMessages messages(String zone) {
        AppProperties p = new AppProperties();
        p.getSchedule().setZoneId(zone);
        p.getNotification().setLocale(AppProperties.NotificationLocale.ZH);
        return new NotificationMessages(p);
    }

    private NotificationContext context() {
        return NotificationContext.builder()
                .targetId(32063L)
                .instanceId(32069L)
                .targetName("163邮件完成通知测试")
                .status(InstanceStatus.SUCCESS)
                .startTime(LocalDateTime.of(2026, 10, 6, 14, 55, 55))
                .endTime(LocalDateTime.of(2026, 10, 6, 14, 55, 56))
                .duration(1000L)
                .build();
    }

    @Test
    void usesExecutionNameAndBusinessTimezoneAcrossChannels() throws Exception {
        NotifyMessage message = new TaskNotifyTemplate(messages("Asia/Shanghai")).build(context());
        NotifyMessage email = message.forChannel(NotifyType.EMAIL);
        assertEquals("【iJob｜执行成功】任务：163邮件完成通知测试", email.getSubject());
        assertTrue(email.getContent().contains("2026-10-06 22:55:55"));
        assertTrue(email.getContent().contains("2026-10-06 22:55:56"));
        assertTrue(email.getContent().contains("北京时间 · Asia/Shanghai · UTC+08:00"));
        assertTrue(email.getContent().contains("1 秒"));
        assertFalse(email.getContent().contains("null"));
        assertEquals(email.getSubject(), message.forChannel(NotifyType.DINGTALK).getSubject());
        assertFalse(message.forChannel(NotifyType.DINGTALK).getContent().contains("<html>"));
        preview("success", email.getContent());
    }

    @Test
    void escapesRuntimeTextAndHighlightsFailureWithoutBreakingDeliveryLimits() throws Exception {
        NotificationContext c = context();
        c.setTargetName("<img src=x onerror=alert(1)>\r\n" + "&测试".repeat(150));
        c.setStatus(InstanceStatus.FAILED);
        c.setMessage("<script>alert('bad')</script>\n" + "&\"'".repeat(2000));
        NotifyMessage email =
                new JobNotifyTemplate(messages("Asia/Shanghai"))
                        .build(c)
                        .forChannel(NotifyType.EMAIL);
        assertTrue(email.getSubject().startsWith("【iJob｜执行失败】作业："));
        assertTrue(email.getSubject().length() <= 255);
        assertFalse(email.getSubject().contains("\n"));
        assertFalse(email.getSubject().contains("\r"));
        assertFalse(email.getContent().contains("<script>"));
        assertFalse(email.getContent().contains("<img"));
        assertTrue(email.getContent().contains("&lt;script&gt;"));
        assertTrue(email.getContent().contains("#c93636"));
        assertTrue(email.getContent().length() <= 10000);
        preview("failure-long", email.getContent());
    }

    @Test
    void handlesMissingFieldsAndUtcWithoutNullOrInventedTimes() throws Exception {
        NotificationContext c =
                NotificationContext.builder().status(InstanceStatus.TIMED_OUT).build();
        NotifyMessage email =
                new TaskNotifyTemplate(messages("UTC")).build(c).forChannel(NotifyType.EMAIL);
        assertEquals("【iJob｜执行超时】任务：名称不可用", email.getSubject());
        assertTrue(email.getContent().contains("—"));
        assertFalse(email.getContent().contains("null"));
        assertFalse(email.getContent().contains("1970"));
        preview("missing", email.getContent());
    }

    @Test
    void daylightSavingTransitionKeepsBothOffsetsAndElapsedDuration() {
        NotificationContext c = context();
        c.setStartTime(LocalDateTime.of(2026, 11, 1, 5, 30));
        c.setEndTime(LocalDateTime.of(2026, 11, 1, 6, 30));
        c.setDuration(3600000L);
        String body =
                new JobNotifyTemplate(messages("America/New_York")).build(c).getEmailContent();
        assertTrue(body.contains("2026-11-01 01:30:00"));
        assertTrue(body.contains("UTC-04:00 → UTC-05:00"));
        assertTrue(body.contains("1 小时"));
    }

    private void preview(String name, String html) throws Exception {
        String output = System.getProperty("ijob.mailPreviewDir");
        if (output != null) {
            Files.createDirectories(Path.of(output));
            Files.writeString(Path.of(output, name + ".html"), html);
        }
    }
}
