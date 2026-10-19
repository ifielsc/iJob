// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.push.publisher;

public final class PayloadQuota {
    private final int maxMessageBytes;
    private final long maxMessages;
    private final long maxTotalBytes;
    private long messageCount;
    private long payloadBytes;

    public PayloadQuota(int maxMessageBytes, long maxMessages, long maxTotalBytes) {
        if (maxMessageBytes < 1 || maxMessages < 1 || maxTotalBytes < maxMessageBytes) {
            throw new IllegalArgumentException("invalid push payload quota");
        }
        this.maxMessageBytes = maxMessageBytes;
        this.maxMessages = maxMessages;
        this.maxTotalBytes = maxTotalBytes;
    }

    static PayloadQuota unlimited() {
        return new PayloadQuota(Integer.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);
    }

    public synchronized void accept(SerializedPayload payload) {
        if (payload == null || payload.getData() == null) {
            throw new IllegalStateException("push message is not serialized");
        }
        int bytes = payload.getData().length;
        if (bytes > maxMessageBytes) {
            throw new IllegalStateException("push message exceeds maxMessageBytes");
        }
        if (messageCount >= maxMessages) {
            throw new IllegalStateException("push message count exceeds limit");
        }
        if (bytes > maxTotalBytes - payloadBytes) {
            throw new IllegalStateException("push payload bytes exceed limit");
        }
        messageCount++;
        payloadBytes += bytes;
    }

    public synchronized long getMessageCount() {
        return messageCount;
    }

    public synchronized long getPayloadBytes() {
        return payloadBytes;
    }
}
