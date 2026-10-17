package com.ifelio.ijob.plugin.push.publisher;

import com.ifelio.ijob.plugin.push.PushMessage;
import com.ifelio.ijob.plugin.push.compress.CompressionService;

import java.util.concurrent.CompletableFuture;

public class CompressPublisher extends AbstractPushPublisher {
    private final CompressionService compressionService;
    private final int threshold;

    public CompressPublisher(
            PushPublisher delegate, CompressionService compressionService, int threshold) {
        super(delegate);

        this.compressionService = compressionService;
        this.threshold = threshold;
    }

    @Override
    public CompletableFuture<Void> publish(PushMessage message) {
        try {
            SerializedPayload payload = message.getSerializedPayload();
            if (payload == null) {
                return failed(new IllegalStateException("message not serialized"));
            }

            if (payload.getSerializedSize() >= threshold && compressionService.support(payload)) {
                SerializedPayload compressed = compressionService.compress(payload);
                message.setSerializedPayload(compressed);
                message.setCompressed(true);
                message.getHeaders().put("content-encoding", compressed.getContentEncoding());
            }
            return delegate.publish(message);
        } catch (Exception e) {
            return failed(e);
        }
    }

    private CompletableFuture<Void> failed(Exception e) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        future.completeExceptionally(e);
        return future;
    }
}
