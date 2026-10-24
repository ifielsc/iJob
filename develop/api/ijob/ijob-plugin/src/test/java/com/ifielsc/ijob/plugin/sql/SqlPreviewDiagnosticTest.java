// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.plugin.sql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class SqlPreviewDiagnosticTest {
    @Test
    void preservesDatabaseWordingAndObjectIdentifiers() {
        String result =
                SqlPreviewDiagnostic.message(
                        "Unknown column 'missing_col' in 'field list'",
                        "SELECT missing_col FROM orders",
                        List.of());
        assertTrue(result.contains("Unknown column 'missing_col'"));
        assertTrue(
                SqlPreviewDiagnostic.message(
                                "ERROR: relation \"missing_table\" does not exist\nPosition: 15",
                                "SELECT * FROM missing_table",
                                List.of())
                        .contains("relation \"missing_table\" does not exist"));
    }

    @Test
    void hidesSubmittedValuesAndSqlLiteralsIncludingEscapedAndDollarQuotedForms() {
        String value = "private-" + UUID.randomUUID();
        for (String sql :
                List.of(
                        "SELECT '" + value + "'",
                        "SELECT $tag$" + value + "$tag$",
                        "SELECT q'[" + value + "]'")) {
            String text = SqlPreviewDiagnostic.message("Invalid input " + value, sql, List.of());
            assertFalse(text.contains(value));
            assertTrue(text.contains("[REDACTED]"));
        }
        String text =
                SqlPreviewDiagnostic.message(
                        "Unknown column '" + value + "'", "SELECT :input", List.of(value));
        assertFalse(text.contains(value));
        assertTrue(text.contains("Unknown column"));
        assertFalse(
                SqlPreviewDiagnostic.message(
                                "invalid value O''Brien", "SELECT 'O''Brien'", List.of())
                        .contains("Brien"));
    }

    @Test
    void removesCredentialsUrlsAccountPathsAndUnsubmittedDataValues() {
        String value = UUID.randomUUID().toString();
        String raw =
                "SQL failed password="
                        + value
                        + " token=\""
                        + value
                        + "\" jdbc:mysql://private.invalid/db?user=a&pwd="
                        + value
                        + " user 'private-account'@'private-host' /private/data/secret.csv"
                        + " C:\\private\\secret.txt invalid input syntax: \"private-row-value\"";
        String text = SqlPreviewDiagnostic.message(raw, "SELECT 1", List.of());
        for (String part :
                List.of(
                        value,
                        "private.invalid",
                        "private-account",
                        "private-host",
                        "secret.csv",
                        "secret.txt",
                        "private-row-value")) assertFalse(text.contains(part), part);
        assertTrue(text.contains("SQL failed"));
    }

    @Test
    void longQuotedValuesDoNotOverflowRegexStack() {
        String value = "z".repeat(60000);
        assertTrue(
                SqlPreviewDiagnostic.message(
                                "Invalid input '" + value + "'",
                                "SELECT '" + value + "'",
                                List.of())
                        .contains("[REDACTED]"));
    }

    @Test
    void boundsOutputAfterRedactionAndRejectsOversizedInput() {
        String value = UUID.randomUUID().toString();
        String text =
                SqlPreviewDiagnostic.message(
                        "x".repeat(4090) + value + "😀".repeat(30),
                        "SELECT :input",
                        List.of(value));
        assertFalse(text.contains(value));
        assertTrue(text.codePointCount(0, text.length()) <= 4097);
        assertFalse(Character.isHighSurrogate(text.charAt(text.length() - 2)));
        assertFalse(
                SqlPreviewDiagnostic.message(value + "x".repeat(65536), "SELECT 1", List.of(value))
                        .contains(value));
        assertNull(SqlPreviewDiagnostic.message(null, "SELECT 1", List.of()));
    }
}
