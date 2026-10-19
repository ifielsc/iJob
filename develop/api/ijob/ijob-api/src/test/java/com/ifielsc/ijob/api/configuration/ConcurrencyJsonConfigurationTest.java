// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.contract.concurrency.ConcurrencyResourceDTO;
import com.ifielsc.ijob.contract.concurrency.TaskConcurrencyPolicyDTO;
import com.ifielsc.ijob.contract.concurrency.TaskConcurrencyPolicyUpdateRequest;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;
import java.util.Map;

class ConcurrencyJsonConfigurationTest {
    @Test
    void nestedIdentifiersAndVersionsRemainExact() throws Exception {
        var builder = new Jackson2ObjectMapperBuilder();
        new ConcurrencyJsonConfiguration().concurrencyIdentifiers().customize(builder);
        var mapper = builder.build();
        var resource = new ConcurrencyResourceDTO();
        resource.setId(Long.MAX_VALUE);
        resource.setVersion(Long.MAX_VALUE);
        resource.setMaxConcurrency(8);
        var task = new TaskConcurrencyPolicyDTO();
        task.setTaskId(Long.MAX_VALUE);
        task.setVersion(0L);
        task.setResources(List.of(resource));
        var node = mapper.readTree(mapper.writeValueAsString(task));
        assertEquals("9223372036854775807", node.path("taskId").textValue());
        assertEquals("0", node.path("version").textValue());
        assertEquals(
                "9223372036854775807", node.path("resources").get(0).path("version").textValue());
        assertTrue(node.path("resources").get(0).path("maxConcurrency").isInt());
        assertTrue(
                mapper.readTree(mapper.writeValueAsString(Map.of("business", Long.MAX_VALUE)))
                        .path("business")
                        .isNumber());
    }

    @Test
    void requestsRejectRoundedOrFractionalIntegers() throws Exception {
        var builder = new Jackson2ObjectMapperBuilder();
        new ConcurrencyJsonConfiguration().concurrencyIdentifiers().customize(builder);
        var mapper = builder.build();
        var request =
                mapper.readValue(
                        "{\"expectedVersion\":\"9223372036854775807\",\"resourceIds\":[\"9007199254740993\"],\"taskMaxConcurrency\":null}",
                        TaskConcurrencyPolicyUpdateRequest.class);
        assertEquals(Long.MAX_VALUE, request.getExpectedVersion());
        assertEquals(9007199254740993L, request.getResourceIds().getFirst());
        for (String json :
                List.of(
                        "{\"expectedVersion\":1.5}",
                        "{\"taskMaxConcurrency\":1.5}",
                        "{\"resourceIds\":[1.5]}",
                        "{\"expectedVersion\":\"9223372036854775808\"}"))
            assertThrows(
                    com.fasterxml.jackson.core.JsonProcessingException.class,
                    () -> mapper.readValue(json, TaskConcurrencyPolicyUpdateRequest.class));
    }
}
