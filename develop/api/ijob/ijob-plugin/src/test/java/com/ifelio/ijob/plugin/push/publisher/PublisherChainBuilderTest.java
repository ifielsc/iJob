package com.ifelio.ijob.plugin.push.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.plugin.push.FutureWindow;
import com.ifelio.ijob.plugin.push.PushMessage;
import com.ifelio.ijob.plugin.push.compress.CompressionService;
import com.ifelio.ijob.plugin.push.retry.RetryExecutor;
import com.ifelio.ijob.plugin.push.retry.RetryPolicy;
import com.ifelio.ijob.plugin.push.serializer.MessageSerializer;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

class PublisherChainBuilderTest {
    @Test
    void drainedFailedWindowMustStillFailFinalFlush() {
        AtomicInteger delivered = new AtomicInteger();
        RecordingPublisher target =
                new RecordingPublisher(
                        message -> {
                            delivered.incrementAndGet();
                            return CompletableFuture.completedFuture(null);
                        });
        PublishMetrics metrics = new PublishMetrics();
        PushPublisher publisher =
                builder()
                        .build(
                                target,
                                new StaticSerializer(),
                                null,
                                policy(0),
                                2,
                                2,
                                new PayloadQuota(1024, 3, 10_240),
                                metrics);
        publisher.publishBatch(
                List.of(
                        PushMessage.builder().payload("one").build(),
                        PushMessage.builder().payload("two").build(),
                        PushMessage.builder().payload("three").build(),
                        PushMessage.builder().payload("four").build()));

        assertThrows(IllegalStateException.class, publisher::flush);
        assertEquals(3, delivered.get());
        assertEquals(3, metrics.getSuccessCount());
        assertEquals(1, metrics.getFailedCount());
        publisher.close();
        assertEquals(1, target.closeCount.get());
    }

    @Test
    void chainShouldSerializeCompressRetryThenTrackFinalResult() {
        List<String> order = new ArrayList<>();
        AtomicInteger targetCalls = new AtomicInteger();
        RecordingPublisher target =
                new RecordingPublisher(
                        message -> {
                            order.add("target");
                            if (targetCalls.incrementAndGet() == 1) {
                                return CompletableFuture.failedFuture(
                                        new IllegalStateException("temporary"));
                            }
                            return CompletableFuture.completedFuture(null);
                        });
        MessageSerializer serializer =
                new MessageSerializer() {
                    @Override
                    public String name() {
                        return "recording";
                    }

                    @Override
                    public SerializedPayload serialize(Object object) {
                        order.add("serialize");
                        byte[] data = new byte[1024 * 1024];
                        return SerializedPayload.builder()
                                .data(data)
                                .contentType("application/json")
                                .originalSize(data.length)
                                .serializedSize(data.length)
                                .build();
                    }
                };
        CompressionService compression =
                new CompressionService() {
                    @Override
                    public String name() {
                        return "recording";
                    }

                    @Override
                    public boolean support(SerializedPayload payload) {
                        return true;
                    }

                    @Override
                    public SerializedPayload compress(SerializedPayload payload) {
                        order.add("compress");
                        return SerializedPayload.builder()
                                .data(payload.getData())
                                .contentType(payload.getContentType())
                                .contentEncoding("gzip")
                                .originalSize(payload.getOriginalSize())
                                .serializedSize(payload.getSerializedSize())
                                .build();
                    }
                };
        PayloadQuota quota = new PayloadQuota(2 * 1024 * 1024, 10, 10 * 1024 * 1024);
        PublishMetrics metrics = new PublishMetrics();
        PushPublisher publisher =
                builder().build(target, serializer, compression, policy(1), 10, 2, quota, metrics);

        PushMessage message = PushMessage.builder().payload("payload").build();
        publisher.publish(message);
        publisher.flush();

        assertEquals(List.of("serialize", "compress", "target", "target"), order);
        assertEquals(2, targetCalls.get());
        assertEquals(1, metrics.getAttemptedCount());
        assertEquals(1, quota.getMessageCount());
        assertEquals(1, metrics.getSuccessCount());
        assertEquals(0, metrics.getFailedCount());
        assertTrue(message.isCompressed());
        assertEquals("gzip", message.getHeaders().get("content-encoding"));
    }

    @Test
    void flushMustWaitForOutcomeAccountingAfterSendCompletes() throws Exception {
        CompletableFuture<Void> sent = new CompletableFuture<>();
        CountDownLatch innerFlushed = new CountDownLatch(1);
        AsyncPublisher inner =
                new AsyncPublisher(
                        new RecordingPublisher(message -> sent),
                        new FutureWindow(2, 5, TimeUnit.SECONDS)) {
                    @Override
                    public void flush() {
                        super.flush();
                        innerFlushed.countDown();
                    }
                };
        PublishMetrics metrics = new PublishMetrics();
        MetricsPublisher publisher = new MetricsPublisher(inner, metrics);
        CompletableFuture<Void> result =
                publisher.publish(PushMessage.builder().payload("payload").build());
        try (var workers = Executors.newFixedThreadPool(2)) {
            CompletableFuture<Void> flushed;
            synchronized (metrics) {
                workers.submit(() -> sent.complete(null));
                flushed = CompletableFuture.runAsync(publisher::flush, workers);
                assertTrue(innerFlushed.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> flushed.get(100, TimeUnit.MILLISECONDS));
                assertFalse(result.isDone());
            }
            flushed.get(5, TimeUnit.SECONDS);
            assertTrue(result.isDone());
            assertEquals(1, metrics.getSuccessCount());
            assertEquals(0, metrics.getFailedCount());
        } finally {
            publisher.close();
        }
    }

    @Test
    void noneCompressionShouldSkipCompressionStage() {
        RecordingPublisher target =
                new RecordingPublisher(message -> CompletableFuture.completedFuture(null));
        PushPublisher publisher =
                builder().build(target, new StaticSerializer(), null, policy(0), 2, 2);
        PushMessage message = PushMessage.builder().payload("payload").build();

        publisher.publish(message);
        publisher.flush();

        assertFalse(message.isCompressed());
        assertEquals("application/json", message.getSerializedPayload().getContentType());
    }

    @Test
    void metricsShouldIncludeSerializationFailures() {
        RecordingPublisher target =
                new RecordingPublisher(message -> CompletableFuture.completedFuture(null));
        PayloadQuota quota = new PayloadQuota(1024, 10, 10_240);
        PublishMetrics metrics = new PublishMetrics();
        MessageSerializer failingSerializer =
                new MessageSerializer() {
                    @Override
                    public String name() {
                        return "failing";
                    }

                    @Override
                    public SerializedPayload serialize(Object object) {
                        throw new IllegalArgumentException("cannot serialize");
                    }
                };
        PushPublisher publisher =
                builder().build(target, failingSerializer, null, policy(0), 2, 2, quota, metrics);

        CompletableFuture<Void> result =
                publisher.publish(PushMessage.builder().payload("payload").build());
        publisher.close();

        assertTrue(result.isCompletedExceptionally());
        assertEquals(1, metrics.getAttemptedCount());
        assertEquals(0, metrics.getSuccessCount());
        assertEquals(1, metrics.getFailedCount());
        assertEquals(0, quota.getMessageCount());
        assertEquals(1, target.closeCount.get());
    }

    @Test
    void closeShouldCancelFinalFutureAndCloseTarget() {
        CompletableFuture<Void> pending = new CompletableFuture<>();
        RecordingPublisher target = new RecordingPublisher(message -> pending);
        PushPublisher publisher =
                builder().build(target, new StaticSerializer(), null, policy(0), 2, 5);

        CompletableFuture<Void> result =
                publisher.publish(PushMessage.builder().payload("payload").build());
        publisher.close();

        assertTrue(result.isCancelled());
        assertTrue(pending.isCancelled());
        assertEquals(1, target.closeCount.get());
    }

    private PublisherChainBuilder builder() {
        return new PublisherChainBuilder(new RetryExecutor());
    }

    private RetryPolicy policy(int retries) {
        return RetryPolicy.builder()
                .maxRetries(retries)
                .initialInterval(0)
                .exponential(false)
                .build();
    }

    private interface PublishAction {
        CompletableFuture<Void> publish(PushMessage message);
    }

    private static final class RecordingPublisher implements PushPublisher {
        private final PublishAction action;
        private final AtomicInteger closeCount = new AtomicInteger();

        private RecordingPublisher(PublishAction action) {
            this.action = action;
        }

        @Override
        public CompletableFuture<Void> publish(PushMessage message) {
            return action.publish(message);
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
        }
    }

    private static final class StaticSerializer implements MessageSerializer {
        @Override
        public String name() {
            return "static";
        }

        @Override
        public SerializedPayload serialize(Object object) {
            byte[] data = object.toString().getBytes(StandardCharsets.UTF_8);
            return SerializedPayload.builder()
                    .data(data)
                    .contentType("application/json")
                    .originalSize(data.length)
                    .serializedSize(data.length)
                    .build();
        }
    }
}
