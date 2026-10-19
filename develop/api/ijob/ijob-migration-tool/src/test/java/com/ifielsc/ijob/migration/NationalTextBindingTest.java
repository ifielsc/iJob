// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.concurrent.atomic.AtomicReference;

class NationalTextBindingTest {
    @Test
    void nationalTextUsesNationalJdbcBindingAndOrdinaryTextRemainsUnchanged() throws Exception {
        verify(Types.NVARCHAR, "setNString");
        verify(Types.NCHAR, "setNString");
        verify(Types.NCLOB, "setNCharacterStream");
        verify(Types.LONGNVARCHAR, "setNCharacterStream");
        verify(Types.VARCHAR, "setString");
        verify(Types.CLOB, "setCharacterStream");
    }

    private void verify(int jdbcType, String expectedMethod) throws Exception {
        AtomicReference<String> called = new AtomicReference<>();
        PreparedStatement statement =
                (PreparedStatement)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {PreparedStatement.class},
                                (proxy, method, args) -> {
                                    called.set(method.getName());
                                    assertEquals(1, args[0]);
                                    if (args[1] instanceof String value) {
                                        assertEquals("显示名-✓", value);
                                    }
                                    return null;
                                });
        var bind =
                JdbcMigrationService.class.getDeclaredMethod(
                        "bind",
                        PreparedStatement.class,
                        int.class,
                        JsonNode.class,
                        BundleManifest.LogicalType.class,
                        int.class);
        bind.setAccessible(true);
        bind.invoke(
                null,
                statement,
                1,
                TextNode.valueOf("显示名-✓"),
                BundleManifest.LogicalType.TEXT,
                jdbcType);
        assertEquals(expectedMethod, called.get());
    }
}
