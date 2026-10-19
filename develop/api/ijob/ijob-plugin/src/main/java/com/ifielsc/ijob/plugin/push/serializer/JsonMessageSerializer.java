package com.ifielsc.ijob.plugin.push.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.plugin.push.publisher.SerializedPayload;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonMessageSerializer implements MessageSerializer {
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "json";
    }

    @Override
    public SerializedPayload serialize(Object object) throws Exception {
        byte[] bytes = objectMapper.writeValueAsBytes(object);
        return SerializedPayload.builder()
                .data(bytes)
                .contentType("application/json")
                .originalSize(bytes.length)
                .serializedSize(bytes.length)
                .build();
    }
}
