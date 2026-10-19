// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifielsc.ijob.plugin.etl.sql.NamedSql;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

class PluginConfigCodecTest {
    @Test
    void etlResolutionRoundTripRetainsExplicitNullAndExactNumbersWithHttpMapperDefaults() {
        var httpMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        var codec = new PluginConfigCodec(httpMapper);
        var input =
                codec.read(
                        "{\"source\":{\"parameters\":{\"optional\":null,\"amount\":123456789012345678.123456789012,\"id\":9007199254740993}}}",
                        EtlTaskConfig.class,
                        "ETL");
        Map<String, Object> tree = codec.toMap(input, "ETL");
        var resolved = codec.convert(tree, EtlTaskConfig.class, "ETL");
        var parameters = resolved.getSource().getParameters();
        assertTrue(parameters.containsKey("optional"));
        assertNull(parameters.get("optional"));
        assertEquals(new BigDecimal("123456789012345678.123456789012"), parameters.get("amount"));
        assertEquals(new BigInteger("9007199254740993"), parameters.get("id"));
        var sql = NamedSql.parse("SELECT :optional, :amount, :id", parameters);
        assertEquals(3, sql.values().size());
        assertNull(sql.values().get(0));
        assertEquals(
                "{}",
                httpMapper
                        .valueToTree(java.util.Collections.singletonMap("optional", null))
                        .toString());
    }
}
