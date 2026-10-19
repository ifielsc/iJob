package com.ifielsc.ijob.plugin.push.publisher;

import com.ifielsc.ijob.plugin.push.FutureWindow;
import com.ifielsc.ijob.plugin.push.PushMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AsyncPublisher extends AbstractPushPublisher {
    private final FutureWindow window;

    public AsyncPublisher(PushPublisher delegate, FutureWindow window) {
        super(delegate);

        this.window = window;
    }

    @Override
    public CompletableFuture<Void> publish(PushMessage message) {
        CompletableFuture<Void> future = delegate.publish(message);
        window.add(future);
        return future;
    }

    @Override
    public CompletableFuture<Void> publishBatch(List<PushMessage> messages) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (PushMessage message : messages) {
            CompletableFuture<Void> future = delegate.publish(message);
            futures.add(future);
            window.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    @Override
    public void flush() {
        window.flush();
        super.flush();
    }

    @Override
    public void close() {
        window.close();
        super.close();
    }
}
