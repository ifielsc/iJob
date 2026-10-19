package com.ifielsc.ijob.plugin.push.publisher;

import com.ifielsc.ijob.plugin.push.PushMessage;
import com.ifielsc.ijob.plugin.push.retry.RetryExecutor;
import com.ifielsc.ijob.plugin.push.retry.RetryPolicy;

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
