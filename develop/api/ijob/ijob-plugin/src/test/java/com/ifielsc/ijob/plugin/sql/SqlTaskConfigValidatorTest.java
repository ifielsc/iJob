// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.sql;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SqlTaskConfigValidatorTest {
    private final SqlTaskConfigValidator validator = new SqlTaskConfigValidator();

    @Test
    void validConfigurationShouldPass() {
        SqlTaskConfig config = config();
        config.setMinRows(0);
        config.setMaxRows(10);
        config.setResultVariable("result");

        assertDoesNotThrow(() -> validator.validate(config));
    }

    @Test
    void resourceScriptAndDelimiterShouldBeRequired() {
        SqlTaskConfig missingResource = config();
        missingResource.setResourceId(0L);
        SqlTaskConfig missingScript = config();
        missingScript.setScript("  ");
        SqlTaskConfig missingDelimiter = config();
        missingDelimiter.setDelimiter("");

        assertEquals(
                "SQL resourceId must be positive",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> validator.validate(missingResource))
                        .getMessage());
        assertEquals(
                "SQL script is required",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> validator.validate(missingScript))
                        .getMessage());
        assertEquals(
                "SQL delimiter is invalid",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> validator.validate(missingDelimiter))
                        .getMessage());
    }

    @Test
    void scriptSizeAndNullCharacterShouldBeRejected() {
        SqlTaskConfig oversized = config();
        oversized.setScript("x".repeat(1_000_001));
        SqlTaskConfig nullCharacter = config();
        nullCharacter.setScript("select\0value");

        assertEquals(
                "SQL script is invalid",
                assertThrows(IllegalArgumentException.class, () -> validator.validate(oversized))
                        .getMessage());
        assertEquals(
                "SQL script is invalid",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> validator.validate(nullCharacter))
                        .getMessage());
    }

    @Test
    void timeoutAndRowRangeShouldBeBounded() {
        SqlTaskConfig timeout = config();
        timeout.setQueryTimeoutSeconds(0);
        SqlTaskConfig negativeRows = config();
        negativeRows.setMinRows(-1);
        SqlTaskConfig invertedRows = config();
        invertedRows.setMinRows(3);
        invertedRows.setMaxRows(2);

        assertThrows(IllegalArgumentException.class, () -> validator.validate(timeout));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(negativeRows));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(invertedRows));
    }

    @Test
    void blankResultVariableShouldFail() {
        SqlTaskConfig config = config();
        config.setResultVariable(" ");

        assertEquals(
                "SQL result variable is invalid",
                assertThrows(IllegalArgumentException.class, () -> validator.validate(config))
                        .getMessage());
    }

    @Test
    void delimiterAndOutputVariableMustRemainStatic() {
        SqlTaskConfig delimiter = config();
        delimiter.setDelimiter("${delimiter}");
        SqlTaskConfig variable = config();
        variable.setResultVariable("${output}");

        assertThrows(IllegalArgumentException.class, () -> validator.validate(delimiter));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(variable));
    }

    @Test
    void continueOnErrorShouldRequireAutoCommitMode() {
        SqlTaskConfig config = config();
        config.setContinueOnError(true);

        assertEquals(
                "SQL continueOnError requires transaction=false",
                assertThrows(IllegalArgumentException.class, () -> validator.validate(config))
                        .getMessage());
    }

    private SqlTaskConfig config() {
        SqlTaskConfig config = new SqlTaskConfig();
        config.setResourceId(1L);
        config.setScript("select 1;");
        return config;
    }
}
