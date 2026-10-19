// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.mq.rabbit;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

@Slf4j
public class RabbitChannelPool {
    private final ObjectPool<Channel> pool;
    private final boolean publisherConfirm;

    public RabbitChannelPool(Connection connection, int maxTotal) {
        this(connection, maxTotal, true);
    }

    public RabbitChannelPool(Connection connection, int maxTotal, boolean publisherConfirm) {
        this.publisherConfirm = publisherConfirm;
        GenericObjectPoolConfig<Channel> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(maxTotal);
        config.setMinIdle(2);
        config.setMaxIdle(maxTotal);
        config.setTestOnBorrow(true);
        config.setTestOnReturn(true);

        this.pool =
                new GenericObjectPool<>(
                        new BasePooledObjectFactory<>() {
                            @Override
                            public Channel create() throws Exception {
                                Channel channel = connection.createChannel();
                                if (publisherConfirm) {
                                    channel.confirmSelect();
                                }
                                return channel;
                            }

                            @Override
                            public PooledObject<Channel> wrap(Channel channel) {
                                return new DefaultPooledObject<>(channel);
                            }

                            @Override
                            public void destroyObject(PooledObject<Channel> pooled)
                                    throws Exception {
                                Channel channel = pooled.getObject();
                                if (channel.isOpen()) {
                                    channel.close();
                                }
                            }

                            @Override
                            public boolean validateObject(PooledObject<Channel> pooled) {
                                return pooled.getObject().isOpen();
                            }
                        },
                        config);
    }

    public Channel borrow() throws Exception {
        return pool.borrowObject();
    }

    public void release(Channel channel) {
        release(channel, channel != null && channel.isOpen());
    }

    public void release(Channel channel, boolean healthy) {
        if (channel == null) {
            return;
        }
        try {
            if (healthy && channel.isOpen()) {
                pool.returnObject(channel);
            } else {
                pool.invalidateObject(channel);
            }
        } catch (Exception e) {
            log.warn(
                    "return rabbit channel failed",
                    com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(e));
            try {
                pool.invalidateObject(channel);
            } catch (Exception invalidateException) {
                e.addSuppressed(invalidateException);
            }
        }
    }

    public boolean isPublisherConfirm() {
        return publisherConfirm;
    }

    public void close() {
        try {
            pool.close();
        } catch (Exception e) {
            log.warn(
                    "close rabbit channel pool failed",
                    com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(e));
        }
    }
}
