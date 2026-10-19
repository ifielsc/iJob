// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.push.publisher;

import com.ifielsc.ijob.plugin.push.PushMessage;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class KafkaPublisher implements PushPublisher {
    private final Producer<String, byte[]> producer;
    private final String topic;
    private final AutoCloseable release;
    private final AtomicBoolean closed = new AtomicBoolean();

    public KafkaPublisher(Producer<String, byte[]> producer, String topic, AutoCloseable release) {
        this.producer = producer;
        this.topic = topic;
        this.release = release;
    }

    @Override
    public CompletableFuture<Void> publish(PushMessage message) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            byte[] data = message.getSerializedPayload().getData();
            ProducerRecord<String, byte[]> record =
                    new ProducerRecord<>(topic, message.getKey(), data);
            addHeader(record, "content-type", message.getSerializedPayload().getContentType());
            addHeader(
                    record,
                    "content-encoding",
                    message.getSerializedPayload().getContentEncoding());
            producer.send(
                    record,
                    (metadata, exception) -> {
                        if (exception != null) {
                            future.completeExceptionally(exception);
                        } else {
                            future.complete(null);
                        }
                    });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public void flush() {
        producer.flush();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                release.close();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("kafka publisher close failed", e);
            }
        }
    }

    private void addHeader(ProducerRecord<String, byte[]> record, String name, String value) {
        if (value != null && !value.isBlank()) {
            record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
