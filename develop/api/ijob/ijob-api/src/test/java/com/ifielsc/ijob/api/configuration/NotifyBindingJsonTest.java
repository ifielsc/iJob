package com.ifielsc.ijob.api.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.ifielsc.ijob.contract.notify.NotifyBindingDTO;
import com.ifielsc.ijob.contract.task.TaskDetailDTO;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;

class NotifyBindingJsonTest {
    @Test
    void bindingIdsStayExactInListsAndNestedTaskDetails() throws Exception {
        var builder = new Jackson2ObjectMapperBuilder();
        new ConfigurationJsonConfiguration().configurationIdentifiers().customize(builder);
        var mapper = builder.build();
        NotifyBindingDTO binding = new NotifyBindingDTO();
        binding.setId(Long.MAX_VALUE);
        binding.setTargetId(9007199254740993L);
        binding.setNotifyConfigId(Long.MAX_VALUE - 1);
        TaskDetailDTO task = new TaskDetailDTO();
        task.setNotifyBindings(List.of(binding));
        JsonNode standalone = mapper.valueToTree(List.of(binding));
        JsonNode nested = mapper.valueToTree(task);
        for (JsonNode value : List.of(standalone.get(0), nested.get("notifyBindings").get(0))) {
            for (String field : List.of("id", "targetId", "notifyConfigId")) {
                assertTrue(value.get(field).isTextual());
            }
            assertEquals("9223372036854775807", value.get("id").asText());
            assertEquals("9007199254740993", value.get("targetId").asText());
            assertEquals("9223372036854775806", value.get("notifyConfigId").asText());
        }
    }
}
