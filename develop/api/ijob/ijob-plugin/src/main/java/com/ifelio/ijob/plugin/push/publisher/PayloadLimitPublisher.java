package com.ifelio.ijob.plugin.push.publisher;

import com.ifelio.ijob.plugin.push.PushMessage;

import java.util.concurrent.CompletableFuture;

public class PayloadLimitPublisher extends AbstractPushPublisher {
    private final PayloadQuota quota;

    public PayloadLimitPublisher(PushPublisher delegate, PayloadQuota quota) {
        super(delegate);
        this.quota = quota;
    }

    @Override
    public CompletableFuture<Void> publish(PushMessage message) {
        try {
            quota.accept(message.getSerializedPayload());
            return delegate.publish(message);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
