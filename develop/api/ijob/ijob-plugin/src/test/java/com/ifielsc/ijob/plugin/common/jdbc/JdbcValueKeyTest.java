package com.ifielsc.ijob.plugin.common.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

class JdbcValueKeyTest {
    @Test
    void shouldNormalizeNumericBinaryAndTemporalJdbcValues() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 8, 29, 12, 30);

        assertEquals(JdbcValueKey.of(1), JdbcValueKey.of(1L));
        assertEquals(JdbcValueKey.of(new BigDecimal("1.00")), JdbcValueKey.of(1));
        assertEquals(JdbcValueKey.of(new byte[] {1, 2}), JdbcValueKey.of(new byte[] {1, 2}));
        assertEquals(JdbcValueKey.of(Timestamp.valueOf(timestamp)), JdbcValueKey.of(timestamp));
        assertTrue(JdbcValueKey.valuesEqual(1, 1L));
    }

    @Test
    void shouldKeepCompositeOrderAndValueTypesDistinct() {
        assertNotEquals(
                JdbcValueKey.ofValues(List.of(1, "A")), JdbcValueKey.ofValues(List.of("A", 1)));
        assertNotEquals(JdbcValueKey.of("1"), JdbcValueKey.of(1));
    }
}
