// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.message;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfigValidator;

import org.junit.jupiter.api.Test;

class MessageConfigTest extends MessageTestSupport {
    private final EtlTaskConfigValidator validator = new EtlTaskConfigValidator();

    MessageConfigTest() throws Exception {}

    @Test
    void requiresV2ForMessagesAndKeepsV1ForLegacy() {
        assertDoesNotThrow(() -> validator.validate(config));
        config.setVersion(1);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
        config.getSource().setType(EtlTaskConfig.SourceType.DATABASE);
        config.getSource().setSql("SELECT 1");
        assertDoesNotThrow(() -> validator.validate(config));
    }

    @Test
    void rejectsIncrementalAndNonIdempotentMessageTargetsBeforeIo() {
        config.getIncremental().setEnabled(true);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
        config.getIncremental().setEnabled(false);
        config.getTarget().setMode(EtlTaskConfig.LoadMode.APPEND);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
        config.getTarget().setMode(EtlTaskConfig.LoadMode.UPSERT);
        config.getTarget().setTransactionScope(EtlTaskConfig.TransactionScope.RUN);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
    }

    @Test
    void validatesMessageLimitsEvenWhenOtherValuesContainTemplates() {
        config.getMessage().setMaxMessages(0);
        assertThrows(IllegalArgumentException.class, () -> validator.validateUnresolved(config));
        config.getMessage().setMaxMessages(1);
        config.getSource().setGroupId("${group}");
        assertThrows(IllegalArgumentException.class, () -> validator.validateUnresolved(config));
    }
}
