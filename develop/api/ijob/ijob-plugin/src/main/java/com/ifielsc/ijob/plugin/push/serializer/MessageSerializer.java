package com.ifielsc.ijob.plugin.push.serializer;

import com.ifielsc.ijob.plugin.push.publisher.SerializedPayload;

public interface MessageSerializer {
    String name();

    SerializedPayload serialize(Object object) throws Exception;
}
