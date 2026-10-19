// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.push.factory;

import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.core.resource.ResourceClientLease;
import com.ifielsc.ijob.core.resource.ResourceRuntimeService;
import com.ifielsc.ijob.core.resource.factory.mq.kafka.KafkaClient;
import com.ifielsc.ijob.plugin.push.publisher.KafkaPublisher;
import com.ifielsc.ijob.plugin.push.publisher.PushPublisher;
import com.ifielsc.ijob.plugin.push.resolver.PushTaskPublishConfig;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class KafkaPublisherFactory implements PushPublisherFactory {
    private final ResourceRuntimeService runtimeService;

    @Override
    public ResourceType supportType() {
        return ResourceType.KAFKA;
    }

    @Override
    public PushPublisher create(PushTaskPublishConfig config) {
        ResourceClientLease<KafkaClient> lease =
                runtimeService.leaseClient(
                        config.getResourceId(), ResourceType.KAFKA, KafkaClient.class);
        Map<String, Object> props = config.getProperties();
        return new KafkaPublisher(
                lease.getClient().getProducer(), (String) props.get("topic"), lease);
    }
}
