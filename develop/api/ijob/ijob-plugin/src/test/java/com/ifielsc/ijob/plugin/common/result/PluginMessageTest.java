// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.common.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.core.execute.task.TaskResult;
import com.ifielsc.ijob.core.notify.NotificationContext;
import com.ifielsc.ijob.core.notify.template.NotificationMessages;
import com.ifielsc.ijob.core.notify.template.TaskNotifyTemplate;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.ResourceBundle;

class PluginMessageTest {
    @Test
    void catalogHasBothLanguagesAndUniqueCodes() {
        var codes = new HashSet<String>();
        var keys = new HashSet<String>();
        var control =
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT);
        var en = ResourceBundle.getBundle("messages", Locale.ENGLISH, control);
        var zh = ResourceBundle.getBundle("messages", Locale.CHINESE, control);
        for (var message : PluginMessage.values()) {
            assertTrue(codes.add(message.errorCode()));
            assertTrue(keys.add(message.messageKey()));
            assertEquals(message.defaultMessage(), en.getString(message.messageKey()));
            assertNotEquals(message.defaultMessage(), zh.getString(message.messageKey()));
        }
    }

    @Test
    void resultRetainsEnglishDiagnosticAndParameterizedMetadata() {
        var result = TaskResult.fail(PluginMessage.API_STATUS, 503);
        assertEquals("api invoke failed status:503", result.getMessage());
        assertEquals("[503]", result.getMessageMetadata().messageArgs());
        var properties = new AppProperties();
        properties.getNotification().setLocale(AppProperties.NotificationLocale.ZH);
        var context =
                NotificationContext.builder()
                        .status(result.getStatus())
                        .message(result.getMessage())
                        .messageKey(result.getMessageMetadata().messageKey())
                        .messageArgs(new Object[] {503})
                        .build();
        assertTrue(
                new TaskNotifyTemplate(new NotificationMessages(properties))
                        .build(context)
                        .getContent()
                        .contains("API 调用失败，状态码：503"));
    }
}
