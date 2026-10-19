// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.common.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.DatabaseMetaData;
import java.util.List;

class JdbcIdentifierQuoterTest {
    @Test
    void shouldQuoteQualifiedNamesAndLists() throws Exception {
        DatabaseMetaData metadata = metadata(" ` ");
        JdbcIdentifierQuoter quoter = JdbcIdentifierQuoter.from(metadata);
        StringBuilder sql = new StringBuilder();

        quoter.append(sql, List.of("id", "name"));

        assertEquals("`sales`.`orders`", quoter.qualify("sales", "orders"));
        assertEquals("`id`,`name`", sql.toString());
    }

    @Test
    void shouldSupportDatabasesWithoutIdentifierQuotes() throws Exception {
        JdbcIdentifierQuoter quoter = JdbcIdentifierQuoter.from(metadata(" "));

        assertEquals("orders", quoter.qualify(null, "orders"));
    }

    private DatabaseMetaData metadata(String quote) {
        return (DatabaseMetaData)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {DatabaseMetaData.class},
                        (proxy, method, args) ->
                                method.getName().equals("getIdentifierQuoteString") ? quote : null);
    }
}
