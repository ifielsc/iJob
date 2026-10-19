package com.ifielsc.ijob.core.resource.mq;

/** Run-scoped consumer settings; credentials remain inside the resource adapter. */
public record MessageSourceOptions(
        String destination,
        String groupId,
        String initialPosition,
        int batchSize,
        int prefetch,
        int maxMessageBytes,
        long maxBufferBytes) {}
