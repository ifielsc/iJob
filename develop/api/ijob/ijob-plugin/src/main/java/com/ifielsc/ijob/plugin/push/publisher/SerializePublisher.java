package com.ifielsc.ijob.plugin.push.publisher;

import com.ifielsc.ijob.plugin.push.PushMessage;
import com.ifielsc.ijob.plugin.push.serializer.MessageSerializer;

import java.util.concurrent.CompletableFuture;

public class SerializePublisher extends AbstractPushPublisher {
    private final MessageSerializer serializer;

    public SerializePublisher(PushPublisher delegate, MessageSerializer serializer) {
        super(delegate);

        this.serializer = serializer;
    }

    @Override
    public CompletableFuture<Void> publish(PushMessage message) {
        try {
            SerializedPayload payload = serializer.serialize(message.getPayload());
            message.setSerializedPayload(payload);
            return delegate.publish(message);
        } catch (Exception e) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }
}
