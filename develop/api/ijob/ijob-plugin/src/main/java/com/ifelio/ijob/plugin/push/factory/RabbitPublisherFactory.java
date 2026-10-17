package com.ifelio.ijob.plugin.push.factory;

import com.ifelio.ijob.contract.enums.ResourceType;
import com.ifelio.ijob.core.resource.ResourceClientLease;
import com.ifelio.ijob.core.resource.ResourceRuntimeService;
import com.ifelio.ijob.core.resource.factory.mq.rabbit.RabbitClient;
import com.ifelio.ijob.plugin.push.publisher.PushPublisher;
import com.ifelio.ijob.plugin.push.publisher.RabbitPublisher;
import com.ifelio.ijob.plugin.push.resolver.PushTaskPublishConfig;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class RabbitPublisherFactory implements PushPublisherFactory {
    private final ResourceRuntimeService runtimeService;

    @Override
    public ResourceType supportType() {
        return ResourceType.RABBITMQ;
    }

    @Override
    public PushPublisher create(PushTaskPublishConfig config) {
        ResourceClientLease<RabbitClient> lease =
                runtimeService.leaseClient(
                        config.getResourceId(), ResourceType.RABBITMQ, RabbitClient.class);
        Map<String, Object> props = config.getProperties();
        return new RabbitPublisher(
                lease.getClient().getChannelPool(),
                (String) props.get("exchange"),
                (String) props.get("routingKey"),
                lease);
    }
}
