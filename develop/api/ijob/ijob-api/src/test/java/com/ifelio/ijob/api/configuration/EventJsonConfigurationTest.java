package com.ifelio.ijob.api.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.contract.event.EventWaitDTO;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.Map;

class EventJsonConfigurationTest {
    @Test
    void allApprovalIdentifiersStayExactAndBusinessNumbersStayNumeric() throws Exception {
        var builder = new Jackson2ObjectMapperBuilder();
        new EventJsonConfiguration().eventIdentifiers().customize(builder);
        var mapper = builder.build();
        var wait = new EventWaitDTO();
        wait.setId(Long.MAX_VALUE);
        wait.setInstanceId(Long.MAX_VALUE);
        wait.setRootInstanceId(Long.MAX_VALUE);
        wait.setMatchedEventId(Long.MAX_VALUE);
        wait.setTaskId(Long.MAX_VALUE);
        wait.setTaskDefinitionId(Long.MAX_VALUE);
        wait.setAttemptNo(2);
        var result = mapper.readTree(mapper.writeValueAsString(wait));
        for (String field :
                new String[] {
                    "id",
                    "instanceId",
                    "rootInstanceId",
                    "matchedEventId",
                    "taskId",
                    "taskDefinitionId"
                }) {
            assertTrue(result.get(field).isTextual());
            assertEquals("9223372036854775807", result.get(field).asText());
        }
        assertTrue(result.get("attemptNo").isInt());
        assertTrue(
                mapper.readTree(mapper.writeValueAsString(Map.of("number", Long.MAX_VALUE)))
                        .get("number")
                        .isNumber());
    }
}
