package com.ifielsc.ijob.plugin.etl.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.Map;

class NamedSqlTest {
    @Test
    void bindsExplicitNullWithoutConvertingItToOmission() {
        var parameters = new java.util.LinkedHashMap<String, Object>();
        parameters.put("value", null);
        NamedSql sql = NamedSql.parse("SELECT :value", parameters);
        assertEquals("SELECT ?", sql.sql());
        assertEquals(java.util.Arrays.asList((Object) null), sql.values());
    }

    @Test
    void shouldBindNamesWithoutChangingQuotedTextOrPostgresCast() {
        NamedSql sql =
                NamedSql.parse(
                        "SELECT ':ignored', value::text FROM t WHERE id=:id", Map.of("id", 7));

        assertEquals("SELECT ':ignored', value::text FROM t WHERE id=?", sql.sql());
        assertEquals(java.util.List.of(7), sql.values());
    }
}
