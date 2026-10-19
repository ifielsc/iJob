// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.push.resolver;

import com.ifielsc.ijob.contract.enums.ResourceType;

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
