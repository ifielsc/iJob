// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.push.resolver;

import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.plugin.push.factory.PushPublisherFactory;
import com.ifielsc.ijob.plugin.push.publisher.PushPublisher;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PushPublisherResolver {
    private final List<PushPublisherFactory> factories;

    private Map<ResourceType, PushPublisherFactory> factoryMap;

    @PostConstruct
    public void init() {
        factoryMap =
                factories.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        PushPublisherFactory::supportType, Function.identity()));
    }

    public PushPublisher resolve(PushTaskPublishConfig config) {
        if (config == null || config.getType() == null) {
            throw new IllegalArgumentException("push target type is required");
        }
        PushPublisherFactory factory = factoryMap.get(config.getType());
        if (factory == null) {
            throw new IllegalArgumentException("push target type is not supported");
        }
        return factory.create(config);
    }
}
