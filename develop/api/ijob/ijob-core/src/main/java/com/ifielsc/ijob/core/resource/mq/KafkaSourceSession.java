package com.ifielsc.ijob.core.resource.mq;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** One IO owner continues polling while the ETL worker writes or pauses. */
public final class KafkaSourceSession implements MessageSourceSession {
    private final MessageSourceOptions options;
    private final ArrayBlockingQueue<MessageDelivery> deliveries;
    private final ArrayBlockingQueue<Commit> commits = new ArrayBlockingQueue<>(1);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread worker;
    private volatile Consumer<byte[], byte[]> consumer;
    private volatile Throwable failure;
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private int outstanding;
    private long bufferedBytes;

    public KafkaSourceSession(
            MessageSourceOptions options, Supplier<Consumer<byte[], byte[]>> factory) {
        this.options = options;
        deliveries = new ArrayBlockingQueue<>(options.batchSize());
        worker = new Thread(() -> run(factory), "ijob-kafka-source");
        worker.setDaemon(true);
        worker.start();
    }

    private void run(Supplier<Consumer<byte[], byte[]>> factory) {
        try {
            Consumer<byte[], byte[]> client = factory.get();
            consumer = client;
            client.subscribe(
                    List.of(options.destination()),
                    new ConsumerRebalanceListener() {
                        @Override
                        public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                            if (!closed.get() && outstanding > 0)
                                throw new IllegalStateException(
                                        "Kafka assignment changed with unconfirmed records");
                        }

                        @Override
                        public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                            Map<TopicPartition, OffsetAndMetadata> previous =
                                    client.committed(new java.util.HashSet<>(partitions));
                            List<TopicPartition> fresh =
                                    partitions.stream()
                                            .filter(p -> previous.get(p) == null)
                                            .toList();
                            if (fresh.isEmpty()) {
                                ready.complete(null);
                                return;
                            }
                            Map<TopicPartition, Long> starts =
                                    "LATEST".equals(options.initialPosition())
                                            ? client.endOffsets(fresh)
                                            : client.beginningOffsets(fresh);
                            Map<TopicPartition, OffsetAndMetadata> initial = new LinkedHashMap<>();
                            starts.forEach(
                                    (p, value) -> {
                                        client.seek(p, value);
                                        initial.put(p, new OffsetAndMetadata(value));
                                    });
                            // This is the explicitly selected initial boundary, not acknowledgement
                            // of data.
                            client.commitSync(initial, Duration.ofSeconds(10));
                            ready.complete(null);
                        }
                    });
            while (!closed.get()) {
                Commit commit = commits.poll();
                if (commit != null) {
                    try {
                        client.commitSync(commit.offsets(), Duration.ofSeconds(10));
                        outstanding -= commit.count();
                        bufferedBytes -= commit.bytes();
                        commit.done().complete(null);
                    } catch (Exception exception) {
                        commit.done().completeExceptionally(exception);
                        throw exception;
                    }
                }
                if (outstanding > 0) client.pause(client.assignment());
                else client.resume(client.assignment());
                for (ConsumerRecord<byte[], byte[]> record : client.poll(Duration.ofMillis(100))) {
                    if (closed.get()) break;
                    if (record.value() == null || record.value().length > options.maxMessageBytes())
                        throw new IllegalArgumentException(
                                "Kafka message is null or exceeds the byte limit");
                    bufferedBytes += record.value().length;
                    if (bufferedBytes > options.maxBufferBytes())
                        throw new IllegalArgumentException("Kafka buffer byte limit exceeded");
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("topic", record.topic());
                    metadata.put("partition", record.partition());
                    metadata.put("offset", record.offset());
                    metadata.put("timestamp", record.timestamp());
                    if (record.key() != null)
                        metadata.put("key", Base64.getEncoder().encodeToString(record.key()));
                    Map<String, Object> headers = new LinkedHashMap<>();
                    record.headers()
                            .forEach(
                                    h ->
                                            headers.put(
                                                    h.key(),
                                                    h.value() == null
                                                            ? null
                                                            : Base64.getEncoder()
                                                                    .encodeToString(h.value())));
                    metadata.put("headers", headers);
                    var encoding = record.headers().lastHeader("content-encoding");
                    var receipt =
                            new Receipt(
                                    new TopicPartition(record.topic(), record.partition()),
                                    record.offset() + 1);
                    if (!deliveries.offer(
                            new MessageDelivery(
                                    record.value(),
                                    encoding == null || encoding.value() == null
                                            ? null
                                            : new String(encoding.value(), StandardCharsets.UTF_8),
                                    metadata,
                                    receipt)))
                        throw new IllegalStateException("Kafka delivery window exceeded");
                    outstanding++;
                }
            }
        } catch (WakeupException exception) {
            if (!closed.get()) failure = exception;
        } catch (Throwable exception) {
            failure = exception;
        } finally {
            closed.set(true);
            ready.completeExceptionally(
                    new IllegalStateException("Kafka subscription unavailable"));
            Commit commit;
            while ((commit = commits.poll()) != null)
                commit.done()
                        .completeExceptionally(new IllegalStateException("Kafka consumer closed"));
            if (consumer != null) {
                try {
                    consumer.close(Duration.ofSeconds(5));
                } catch (RuntimeException exception) {
                    if (failure == null) failure = exception;
                }
            }
        }
    }

    @Override
    public MessageDelivery poll(Duration timeout) {
        check();
        try {
            ready.get(15, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka subscription interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Kafka subscription unavailable", exception);
        }
        try {
            MessageDelivery result = deliveries.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            check();
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka read interrupted", exception);
        }
    }

    @Override
    public void confirm(List<MessageDelivery> batch) {
        check();
        Map<TopicPartition, OffsetAndMetadata> offsets = new LinkedHashMap<>();
        long bytes = 0;
        for (MessageDelivery delivery : batch) {
            Receipt receipt = (Receipt) delivery.receipt();
            offsets.put(receipt.partition(), new OffsetAndMetadata(receipt.nextOffset()));
            bytes += delivery.body().length;
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        if (!commits.offer(new Commit(offsets, batch.size(), bytes, done)))
            throw new IllegalStateException("Concurrent Kafka confirmation");
        try {
            done.get(15, TimeUnit.SECONDS);
            check();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka confirmation interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Kafka confirmation outcome is unknown", exception);
        }
    }

    private void check() {
        if (failure != null) throw new IllegalStateException("Kafka consumption failed", failure);
        if (closed.get()) throw new IllegalStateException("Kafka consumer closed");
    }

    @Override
    public void close() {
        boolean firstClose = closed.compareAndSet(false, true);
        ready.completeExceptionally(new IllegalStateException("Kafka consumer closed"));
        Consumer<byte[], byte[]> current = consumer;
        if (firstClose && current != null) current.wakeup();
        try {
            worker.join(15000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private record Receipt(TopicPartition partition, long nextOffset) {}

    private record Commit(
            Map<TopicPartition, OffsetAndMetadata> offsets,
            int count,
            long bytes,
            CompletableFuture<Void> done) {}
}
