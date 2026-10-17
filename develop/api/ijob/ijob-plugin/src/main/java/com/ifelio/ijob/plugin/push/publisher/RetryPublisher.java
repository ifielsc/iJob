package com.ifelio.ijob.plugin.push.publisher;

import com.ifelio.ijob.plugin.push.PushMessage;
import com.ifelio.ijob.plugin.push.retry.RetryExecutor;
import com.ifelio.ijob.plugin.push.retry.RetryPolicy;

import java.util.concurrent.CompletableFuture;

public class RetryPublisher extends AbstractPushPublisher {
    private final RetryExecutor retryExecutor;
    private final RetryPolicy retryPolicy;

    public RetryPublisher(
            PushPublisher delegate, RetryExecutor retryExecutor, RetryPolicy retryPolicy) {
        super(delegate);

        this.retryExecutor = retryExecutor;
        this.retryPolicy = retryPolicy;
    }

    @Override
    public CompletableFuture<Void> publish(PushMessage message) {
        return retryExecutor.execute(() -> delegate.publish(message), retryPolicy);
    }
}
