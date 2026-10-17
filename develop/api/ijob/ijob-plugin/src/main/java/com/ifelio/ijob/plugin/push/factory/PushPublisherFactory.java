package com.ifelio.ijob.plugin.push.factory;

import com.ifelio.ijob.contract.enums.ResourceType;
import com.ifelio.ijob.plugin.push.publisher.PushPublisher;
import com.ifelio.ijob.plugin.push.resolver.PushTaskPublishConfig;

public interface PushPublisherFactory {
    ResourceType supportType();

    PushPublisher create(PushTaskPublishConfig config);
}
