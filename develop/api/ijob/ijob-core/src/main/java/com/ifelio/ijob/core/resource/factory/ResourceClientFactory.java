package com.ifelio.ijob.core.resource.factory;

import com.ifelio.ijob.contract.enums.ResourceType;
import com.ifelio.ijob.infra.entity.resource.Resource;

public interface ResourceClientFactory {
    ResourceType supportType();

    Object create(Resource resource);

    default com.ifelio.ijob.core.resource.mq.MessageSourceSession createMessageSource(
            Resource resource, com.ifelio.ijob.core.resource.mq.MessageSourceOptions options) {
        throw new IllegalArgumentException("Resource does not support message consumption");
    }

    default void verifyConnection(Object client) throws Exception {}

    default void testConnection(Resource resource) throws Exception {
        Object client = create(resource);
        try {
            verifyConnection(client);
        } finally {
            close(client);
        }
    }

    default void close(Object client) throws Exception {
        if (client instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }
}
