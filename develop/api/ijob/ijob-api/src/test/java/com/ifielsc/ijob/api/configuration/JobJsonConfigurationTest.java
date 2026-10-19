// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.job.DagConditionExpression;
import com.ifielsc.ijob.contract.job.JobPublicationDTO;
import com.ifielsc.ijob.core.execute.condition.DagConditionCodec;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.math.BigInteger;

class JobJsonConfigurationTest {
    private ObjectMapper mapper() {
        var builder = new Jackson2ObjectMapperBuilder();
        new JobJsonConfiguration().jobIdentifiers().customize(builder);
        return builder.build();
    }

    @Test
    void stringifiesOnlyIdentifiersAndPreservesConditionNumbers() throws Exception {
        var mapper = mapper();
        var publication =
                mapper.readTree(
                        mapper.writeValueAsString(
                                new JobPublicationDTO(Long.MAX_VALUE, Long.MAX_VALUE - 1, 7L)));
        assertEquals("9223372036854775807", publication.get("jobId").textValue());
        assertEquals("7", publication.get("revision").textValue());
        String payload =
                "{\"op\":\"EQ\",\"left\":{\"var\":\"input.amount\"},\"right\":{\"literal\":9223372036854775807}}";
        var expression = mapper.readValue(payload, DagConditionExpression.class);
        assertEquals(new BigInteger("9223372036854775807"), expression.getRight().getLiteral());
        var restored = DagConditionCodec.decode(1, DagConditionCodec.encode(expression));
        assertEquals(expression.getRight().getLiteral(), restored.getRight().getLiteral());
        assertTrue(mapper.writeValueAsString(restored).contains("\"literal\":9223372036854775807"));
        var decimal =
                mapper.readValue(
                        payload.replace("9223372036854775807", "0.123456789012345678901"),
                        DagConditionExpression.class);
        assertEquals(new BigDecimal("0.123456789012345678901"), decimal.getRight().getLiteral());
        assertEquals(
                decimal.getRight().getLiteral(),
                DagConditionCodec.decode(1, DagConditionCodec.encode(decimal))
                        .getRight()
                        .getLiteral());
        assertEquals(Integer.class, mapper.readValue("1", Object.class).getClass());
    }
}
