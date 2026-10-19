// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

class SqlScriptParserTest {
    @Test
    void shouldSplitOnlyOutsideQuotedValuesAndIdentifiers() {
        List<String> statements =
                SqlScriptParser.parse(
                        "select ';' as value;"
                                + "select \"a;b\" as value;"
                                + "select `a;b`, [c;d] from sample;",
                        ";");

        assertEquals(3, statements.size());
        assertEquals("select ';' as value", statements.get(0));
        assertEquals("select \"a;b\" as value", statements.get(1));
        assertEquals("select `a;b`, [c;d] from sample", statements.get(2));
    }

    @Test
    void shouldKeepEscapedQuotesInsideStatement() {
        List<String> statements =
                SqlScriptParser.parse("select 'it''s;a', \"a\"\";b\"; select 2;", ";");

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).contains("'it''s;a'"));
        assertTrue(statements.get(0).contains("\"a\"\";b\""));
    }

    @Test
    void shouldIgnoreDelimitersInsideComments() {
        List<String> statements =
                SqlScriptParser.parse(
                        "-- comment ;\nselect 1;"
                                + "/* block ; comment */ select 2;"
                                + "# mysql ; comment\nselect 3;",
                        ";");

        assertEquals(3, statements.size());
        assertTrue(statements.get(0).contains("select 1"));
        assertTrue(statements.get(1).contains("select 2"));
        assertTrue(statements.get(2).contains("select 3"));
    }

    @Test
    void shouldSupportCustomDelimiterAndPostgresDollarQuote() {
        List<String> custom = SqlScriptParser.parse("select 1$$select 2$$", "$$");
        List<String> postgres =
                SqlScriptParser.parse(
                        "create function f() returns void as $body$"
                                + "begin perform 1; perform 2; end"
                                + "$body$ language plpgsql;select 3;",
                        ";");

        assertEquals(List.of("select 1", "select 2"), custom);
        assertEquals(2, postgres.size());
        assertTrue(postgres.get(0).contains("perform 1; perform 2;"));
    }

    @Test
    void commentOnlyAndUnterminatedTokensShouldBeHandled() {
        assertEquals(List.of(), SqlScriptParser.parse("-- only comment ;", ";"));
        assertThrows(
                IllegalArgumentException.class,
                () -> SqlScriptParser.parse("select 'broken;", ";"));
        assertThrows(
                IllegalArgumentException.class,
                () -> SqlScriptParser.parse("select 1 /* broken", ";"));
    }
}
