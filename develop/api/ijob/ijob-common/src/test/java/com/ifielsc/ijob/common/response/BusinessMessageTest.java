// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.common.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.exception.BusinessException;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.ResourceBundle;

class BusinessMessageTest {
    @Test
    void everyAuditedMessageHasAStableCodeAndEnglishAndChineseTranslations() {
        var codes = new HashSet<String>();
        var control =
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT);
        var en = ResourceBundle.getBundle("messages", Locale.ENGLISH, control);
        var zh = ResourceBundle.getBundle("messages", Locale.CHINESE, control);
        for (var message : BusinessMessage.values()) {
            assertTrue(codes.add(message.errorCode()), message.errorCode());
            assertFalse(en.getString(message.messageKey()).isBlank(), message.messageKey());
            assertNotEquals(
                    en.getString(message.messageKey()),
                    zh.getString(message.messageKey()),
                    message.messageKey());
            var exception =
                    BusinessException.localized(ResultCode.PARAM_ERROR, message.defaultMessage());
            assertFalse(exception.isCustomMessage());
            assertEquals(message.messageKey(), exception.getMessageKey());
        }
    }

    @Test
    void unrecognizedDiagnosticsCannotBecomeResponseTextOrArguments() {
        var exception =
                BusinessException.localized(
                        ResultCode.SYSTEM_ERROR,
                        "jdbc:mysql://private/db?password=secret SELECT private_column FROM users");
        assertEquals(ResultCode.SYSTEM_ERROR.messageKey(), exception.getMessageKey());
        assertEquals(0, exception.getMessageArgs().length);
        assertFalse(exception.getMessage().contains("secret"));
        var invalidArgument =
                BusinessException.localized(
                        ResultCode.PARAM_ERROR, "required variable missing:password=secret");
        assertEquals(0, invalidArgument.getMessageArgs().length);
    }
}
