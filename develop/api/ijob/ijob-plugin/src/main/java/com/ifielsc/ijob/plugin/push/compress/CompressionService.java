package com.ifielsc.ijob.plugin.push.compress;

import com.ifielsc.ijob.plugin.push.publisher.SerializedPayload;

public interface CompressionService {
    String name();

    boolean support(SerializedPayload payload);

    SerializedPayload compress(SerializedPayload payload) throws Exception;
}
