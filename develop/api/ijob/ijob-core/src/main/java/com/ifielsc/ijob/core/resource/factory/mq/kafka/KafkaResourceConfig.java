// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.mq.kafka;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(exclude = "bootstrapServers")
public class KafkaResourceConfig {
    private String bootstrapServers;

    /**
     * acks
     *
     * <p>all 1 0
     */
    private String acks = "all";

    /** key serializer */
    private String keySerializer = "org.apache.kafka.common.serialization.StringSerializer";

    /** value serializer */
    private String valueSerializer = "org.apache.kafka.common.serialization.ByteArraySerializer";
}
