package com.ifelio.ijob.plugin.push.compress;

import com.ifelio.ijob.plugin.push.publisher.SerializedPayload;

public interface CompressionService {
    String name();

    boolean support(SerializedPayload payload);

    SerializedPayload compress(SerializedPayload payload) throws Exception;
}
