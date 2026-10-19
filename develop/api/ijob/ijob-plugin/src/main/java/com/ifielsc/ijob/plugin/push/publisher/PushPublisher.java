// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.push.publisher;

import com.ifielsc.ijob.plugin.push.PushMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface PushPublisher extends AutoCloseable {
    /** Returns the asynchronous publication outcome. */
    CompletableFuture<Void> publish(PushMessage message);

    default CompletableFuture<Void> publishBatch(List<PushMessage> messages) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (PushMessage message : messages) {
            future = future.thenCompose(v -> publish(message));
        }
        return future;
    }

    /** Waits for pending publications when implemented; the default has no buffered work. */
    default void flush() {}

    @Override
    default void close() {}
}
