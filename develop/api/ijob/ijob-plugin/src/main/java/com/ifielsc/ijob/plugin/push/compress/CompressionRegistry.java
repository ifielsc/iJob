package com.ifielsc.ijob.plugin.push.compress;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CompressionRegistry {
    private final Map<String, CompressionService> services;

    public CompressionRegistry(List<CompressionService> candidates) {
        Map<String, CompressionService> resolved = new LinkedHashMap<>();
        for (CompressionService candidate : candidates) {
            CompressionService existing = resolved.putIfAbsent(candidate.name(), candidate);
            if (existing != null) {
                throw new IllegalStateException(
                        "duplicate compression service: " + candidate.name());
            }
        }
        services = Map.copyOf(resolved);
    }

    public CompressionService get(String name) {
        CompressionService service = services.get(name);
        if (service == null) {
            throw new IllegalArgumentException("compression not found:" + name);
        }
        return service;
    }
}
