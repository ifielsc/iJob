package com.ifelio.ijob.core.notify.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.contract.enums.NotifyType;
import com.ifelio.ijob.infra.entity.runtime.Instance;
import com.ifelio.ijob.infra.mapper.runtime.InstanceMapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

class PluginNotificationTest {
    private final NotificationMessages messages = new NotificationMessages(new AppProperties());

    @Test
    void usesImmutableInstanceNameAndParentWithRealEventTimeAndSeparateChannels() {
        Instance child = new Instance();
        child.setTargetId(12L);
        child.setTargetName("Snapshot <task> & name");
        child.setParentInstanceId(41L);
        Instance parent = new Instance();
        parent.setTargetId(22L);
        parent.setTargetName("Snapshot job");
        InstanceMapper mapper =
                (InstanceMapper)
                        Proxy.newProxyInstance(
                                InstanceMapper.class.getClassLoader(),
                                new Class<?>[] {InstanceMapper.class},
                                (proxy, method, args) -> {
                                    assertEquals("selectSummaryById", method.getName());
                                    return Long.valueOf(42).equals(args[0]) ? child : parent;
                                });
        var notice =
                new PluginNotification(messages, mapper, null)
                        .build(
                                42L,
                                8L,
                                "Data source",
                                LocalDateTime.of(2026, 10, 16, 14, 3, 18),
                                "Checked at",
                                "Check exceptions",
                                "error",
                                "Failed: 1",
                                List.of(
                                        new PluginNotification.Item(
                                                "<script>alert(1)</script>",
                                                "Error",
                                                List.of(
                                                        new PluginNotification.Field(
                                                                "Actual", "null"),
                                                        new PluginNotification.Field(
                                                                "Expected", "≥ 3")))));
        assertTrue(notice.getContent().contains("Snapshot &lt;task&gt; &amp; name"));
        assertTrue(notice.getContent().contains("Snapshot job"));
        assertTrue(notice.getContent().contains("2026-10-16 22:03:18"));
        assertTrue(
                notice.getContent()
                        .contains(messages.text("email.beijing") + " · Asia/Shanghai · UTC+08:00"));
        assertTrue(
                notice.getEmailContent()
                        .contains(messages.text("email.beijing") + " · Asia/Shanghai · UTC+08:00"));
        assertTrue(notice.getEmailContent().startsWith("<!doctype html>"));
        assertFalse(notice.getEmailContent().contains("<script>"));
        assertFalse(notice.getContent().contains("<html>"));
        assertFalse(notice.getContent().contains("结束时间"));
        assertEquals(notice.getEmailContent(), notice.forChannel(NotifyType.EMAIL).getContent());
        assertEquals(notice.getContent(), notice.forChannel(NotifyType.DINGTALK).getContent());
    }

    @Test
    void boundsItemsAndHostileLongTextWithoutLosingTotals() {
        var items =
                IntStream.range(0, 100)
                        .mapToObj(
                                i ->
                                        new PluginNotification.Item(
                                                "item-" + i,
                                                "ERROR",
                                                List.of(
                                                        new PluginNotification.Field(
                                                                "Actual",
                                                                "中".repeat(10000)
                                                                        + "\n### injected"))))
                        .toList();
        var notice =
                new PluginNotification(messages, null, null)
                        .build(
                                1L,
                                null,
                                "Data source",
                                null,
                                "Checked",
                                "Notice\r\nInjected",
                                "warning",
                                "Failed: 100",
                                items);
        assertTrue(notice.getContent().contains("Failed: 100"));
        assertTrue(notice.getContent().contains("95"));
        assertTrue(notice.getEmailContent().contains("90"));
        assertFalse(notice.getContent().contains("item-5"));
        assertFalse(notice.getEmailContent().contains("item-10"));
        assertTrue(notice.getContent().getBytes(StandardCharsets.UTF_8).length < 18000);
    }
}
