// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.push.serializer;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MessageSerializerRegistry {
    private final Map<String, MessageSerializer> serializers;

    public MessageSerializerRegistry(List<MessageSerializer> candidates) {
        Map<String, MessageSerializer> resolved = new LinkedHashMap<>();
        for (MessageSerializer candidate : candidates) {
            MessageSerializer existing = resolved.putIfAbsent(candidate.name(), candidate);
            if (existing != null) {
                throw new IllegalStateException(
                        "duplicate message serializer: " + candidate.name());
            }
        }
        serializers = Map.copyOf(resolved);
    }

    public MessageSerializer get(String name) {
        MessageSerializer serializer = serializers.get(name);
        if (serializer == null) {
            throw new IllegalArgumentException("serializer not found:" + name);
        }
        return serializer;
    }
}
