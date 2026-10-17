package com.ifelio.ijob.plugin.push.serializer;

import com.ifelio.ijob.plugin.push.publisher.SerializedPayload;

public interface MessageSerializer {
    String name();

    SerializedPayload serialize(Object object) throws Exception;
}
