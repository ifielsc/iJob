package com.ifelio.ijob.core.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.ResourceBundle;

class CoreMessageTest {
    @Test
    void allDefinitionsHaveUniqueStableIdentitiesAndBothTranslations() {
        var codes = new HashSet<String>();
        var keys = new HashSet<String>();
        var control =
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT);
        var en = ResourceBundle.getBundle("messages", Locale.ENGLISH, control);
        var zh = ResourceBundle.getBundle("messages", Locale.CHINESE, control);
        for (CoreMessage message : CoreMessage.values()) {
            assertTrue(codes.add(message.errorCode()), message.errorCode());
            assertTrue(keys.add(message.messageKey()), message.messageKey());
            assertEquals(message.defaultMessage(), en.getString(message.messageKey()));
            assertNotEquals(message.defaultMessage(), zh.getString(message.messageKey()));
            assertFalse(new BusinessException(ResultCode.PARAM_ERROR, message).isCustomMessage());
        }
    }

    @Test
    void argumentFormattingIsEnglishAndDefensive() {
        Object[] args = {"node-A"};
        var exception =
                new BusinessException(
                        ResultCode.PARAM_ERROR, CoreMessage.DUPLICATE_NODE_CODE_VALUE, args);
        args[0] = "changed";
        assertEquals("Duplicate node code: node-A", exception.getMessage());
        assertEquals("node-A", exception.getMessageArgs()[0]);
        assertEquals(CoreMessage.DUPLICATE_NODE_CODE_VALUE.errorCode(), exception.getErrorCode());
    }
}
