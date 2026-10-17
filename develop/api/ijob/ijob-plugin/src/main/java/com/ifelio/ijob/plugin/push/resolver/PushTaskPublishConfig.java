package com.ifelio.ijob.plugin.push.resolver;

import com.ifelio.ijob.contract.enums.ResourceType;

import lombok.Data;

import java.util.Map;

@Data
public class PushTaskPublishConfig {
    /** RabbitMQ or Kafka resource ID. */
    private Long resourceId;

    private ResourceType type;

    /**
     * Rabbit: exchange
     *
     * <p>routingKey
     *
     * <p>Kafka: topic
     */
    private Map<String, Object> properties;
}
