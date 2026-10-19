package com.ifielsc.ijob.plugin.push.factory;

import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.plugin.push.publisher.PushPublisher;
import com.ifielsc.ijob.plugin.push.resolver.PushTaskPublishConfig;

public interface PushPublisherFactory {
    ResourceType supportType();

    PushPublisher create(PushTaskPublishConfig config);
}
