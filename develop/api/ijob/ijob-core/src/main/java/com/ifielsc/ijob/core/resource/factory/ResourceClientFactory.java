// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory;

import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.infra.entity.resource.Resource;

public interface ResourceClientFactory {
    ResourceType supportType();

    Object create(Resource resource);

    default com.ifielsc.ijob.core.resource.mq.MessageSourceSession createMessageSource(
            Resource resource, com.ifielsc.ijob.core.resource.mq.MessageSourceOptions options) {
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
