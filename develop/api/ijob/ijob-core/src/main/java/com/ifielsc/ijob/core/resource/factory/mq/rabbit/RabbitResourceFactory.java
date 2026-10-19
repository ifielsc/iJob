package com.ifielsc.ijob.core.resource.factory.mq.rabbit;

import cn.hutool.json.JSONUtil;

import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.core.resource.factory.ResourceClientFactory;
import com.ifielsc.ijob.infra.entity.resource.Resource;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import org.springframework.stereotype.Component;

@Component
public class RabbitResourceFactory implements ResourceClientFactory {
    @Override
    public ResourceType supportType() {
        return ResourceType.RABBITMQ;
    }

    @Override
    public Object create(Resource resource) {
        try {
            RabbitResourceConfig config =
                    JSONUtil.toBean(resource.getConfig(), RabbitResourceConfig.class);
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(config.getHost());
            factory.setPort(config.getPort());
            factory.setUsername(config.getUsername());
            factory.setPassword(config.getPassword());
            factory.setVirtualHost(config.getVirtualHost());

            Connection connection = factory.newConnection();
            return new RabbitClient(connection, 20, config.isPublisherConfirm());
        } catch (Exception e) {
            throw new IllegalStateException("RabbitMQ client creation failed", e);
        }
    }

    @Override
    public com.ifielsc.ijob.core.resource.mq.MessageSourceSession createMessageSource(
            Resource resource, com.ifielsc.ijob.core.resource.mq.MessageSourceOptions options) {
        RabbitResourceConfig config =
                JSONUtil.toBean(resource.getConfig(), RabbitResourceConfig.class);
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(config.getHost());
        factory.setPort(config.getPort());
        factory.setUsername(config.getUsername());
        factory.setPassword(config.getPassword());
        factory.setVirtualHost(config.getVirtualHost());
        factory.setConnectionTimeout(10000);
        factory.setHandshakeTimeout(10000);
        factory.setAutomaticRecoveryEnabled(false);
        Connection connection = null;
        try {
            connection = factory.newConnection("ijob-etl-source");
            return new com.ifielsc.ijob.core.resource.mq.RabbitSourceSession(connection, options);
        } catch (Exception exception) {
            if (connection != null) connection.abort();
            throw new IllegalStateException(
                    "RabbitMQ consumer creation failed",
                    com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
        }
    }

    @Override
    public void verifyConnection(Object client) {
        if (!((RabbitClient) client).getConnection().isOpen()) {
            throw new IllegalStateException("rabbit connection unavailable");
        }
    }

    @Override
    public void close(Object client) {
        ((RabbitClient) client).close();
    }
}
