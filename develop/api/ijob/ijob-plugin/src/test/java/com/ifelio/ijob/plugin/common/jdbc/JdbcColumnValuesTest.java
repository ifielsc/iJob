package com.ifelio.ijob.plugin.common.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;

class JdbcColumnValuesTest {
    @Test
    void timestampVariableRetainsNanosecondsWithoutInventingTimezone() throws Exception {
        Timestamp timestamp = Timestamp.valueOf("2026-09-30 12:34:56.123456789");
        assertEquals(
                "2026-09-30T12:34:56.123456789",
                JdbcColumnValues.readVariable(
                        resultSet("getTimestamp", timestamp), 1, Types.TIMESTAMP));
        assertNull(
                JdbcColumnValues.readVariable(resultSet("getTimestamp", null), 1, Types.TIMESTAMP));
    }

    @Test
    void timestampUsesPortableGetterAndPreservesFractionalPrecision() throws Exception {
        Timestamp timestamp = Timestamp.valueOf("2026-09-30 12:34:56.123456789");
        ResultSet rows = resultSet("getTimestamp", timestamp);

        Timestamp actual = (Timestamp) JdbcColumnValues.read(rows, 1, Types.TIMESTAMP);

        assertEquals(timestamp, actual);
        assertEquals(123456789, actual.getNanos());
    }

    @Test
    void nullTemporalValuesRemainNull() throws Exception {
        assertNull(JdbcColumnValues.read(resultSet("getTimestamp", null), 1, Types.TIMESTAMP));
        assertNull(JdbcColumnValues.read(resultSet("getDate", null), 1, Types.DATE));
        assertNull(JdbcColumnValues.read(resultSet("getTime", null), 1, Types.TIME));
    }

    @Test
    void dateAndTimeUseStandardJdbcValues() throws Exception {
        java.sql.Date date = java.sql.Date.valueOf("2024-02-29");
        java.sql.Time time = java.sql.Time.valueOf("23:59:59");
        assertEquals(date, JdbcColumnValues.read(resultSet("getDate", date), 1, Types.DATE));
        assertEquals(time, JdbcColumnValues.read(resultSet("getTime", time), 1, Types.TIME));
    }

    @Test
    void exactNumericAndBinaryValuesAreNotCoercedToText() throws Exception {
        BigDecimal decimal = new BigDecimal("12345678901234.123456");
        byte[] bytes = new byte[] {0, 1, -1};
        assertSame(
                decimal, JdbcColumnValues.read(resultSet("getObject", decimal), 1, Types.DECIMAL));
        assertSame(bytes, JdbcColumnValues.read(resultSet("getObject", bytes), 1, Types.VARBINARY));
    }

    private ResultSet resultSet(String getter, Object value) {
        return (ResultSet)
                Proxy.newProxyInstance(
                        ResultSet.class.getClassLoader(),
                        new Class<?>[] {ResultSet.class},
                        (proxy, method, args) -> {
                            assertEquals(getter, method.getName(), "Unexpected JDBC getter");
                            assertEquals(1, args[0]);
                            return value;
                        });
    }
}
