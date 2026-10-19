// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.mq.rabbit;

import com.rabbitmq.client.Connection;

import lombok.Getter;

@Getter
public class RabbitClient {
    private final Connection connection;
    private final RabbitChannelPool channelPool;

    public RabbitClient(Connection connection, int channelPoolSize) {
        this(connection, channelPoolSize, true);
    }

    public RabbitClient(Connection connection, int channelPoolSize, boolean publisherConfirm) {
        this.connection = connection;
        this.channelPool = new RabbitChannelPool(connection, channelPoolSize, publisherConfirm);
    }

    public void close() {
        try {
            channelPool.close();
        } catch (Exception ignored) {
        }

        try {
            if (connection.isOpen()) {
                connection.close();
            }
        } catch (Exception ignored) {
        }
    }
}
