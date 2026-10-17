package com.ifelio.ijob.plugin.push.publisher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.core.resource.factory.mq.rabbit.RabbitChannelPool;
import com.ifelio.ijob.plugin.push.PushMessage;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ReturnListener;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class MessagePublisherTest {
    @Test
    void rabbitShouldPublishPersistentPayloadAndReleaseChannelAndLease() {
        AtomicReference<String> exchange = new AtomicReference<>();
        AtomicReference<String> routingKey = new AtomicReference<>();
        AtomicReference<AMQP.BasicProperties> properties = new AtomicReference<>();
        AtomicReference<byte[]> body = new AtomicReference<>();
        AtomicReference<Boolean> mandatory = new AtomicReference<>();
        Channel channel =
                proxy(
                        Channel.class,
                        (proxy, method, args) -> {
                            if ("basicPublish".equals(method.getName())) {
                                exchange.set((String) args[0]);
                                routingKey.set((String) args[1]);
                                mandatory.set((Boolean) args[2]);
                                properties.set((AMQP.BasicProperties) args[3]);
                                body.set((byte[]) args[4]);
                            }
                            if ("waitForConfirms".equals(method.getName())) {
                                return true;
                            }
                            return defaultValue(method.getReturnType());
                        });
        RecordingChannelPool pool = new RecordingChannelPool(channel);
        AtomicInteger closed = new AtomicInteger();
        RabbitPublisher publisher =
                new RabbitPublisher(pool, "events", "jobs.ready", closed::incrementAndGet);

        publisher.publish(message()).join();
        publisher.close();
        publisher.close();

        assertEquals("events", exchange.get());
        assertEquals("jobs.ready", routingKey.get());
        assertTrue(mandatory.get());
        assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), body.get());
        assertEquals(2, properties.get().getDeliveryMode());
        assertEquals("application/json", properties.get().getContentType());
        assertEquals("gzip", properties.get().getContentEncoding());
        assertEquals(1, pool.releaseCount.get());
        assertTrue(pool.lastHealthy);
        assertEquals(1, closed.get());
    }

    @Test
    void rabbitNackShouldFailAndInvalidateChannel() {
        Channel channel =
                proxy(
                        Channel.class,
                        (proxy, method, args) ->
                                "waitForConfirms".equals(method.getName())
                                        ? false
                                        : defaultValue(method.getReturnType()));
        RecordingChannelPool pool = new RecordingChannelPool(channel);
        RabbitPublisher publisher = new RabbitPublisher(pool, "events", "jobs.ready", () -> {});

        assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> publisher.publish(message()).join());
        assertFalse(pool.lastHealthy);
    }

    @Test
    void rabbitReturnedMessageShouldFailAndInvalidateChannel() {
        AtomicReference<ReturnListener> listener = new AtomicReference<>();
        Channel channel =
                proxy(
                        Channel.class,
                        (proxy, method, args) -> {
                            if ("addReturnListener".equals(method.getName())
                                    && args[0] instanceof ReturnListener value) {
                                listener.set(value);
                            }
                            if ("waitForConfirms".equals(method.getName())) {
                                listener.get()
                                        .handleReturn(
                                                312,
                                                "NO_ROUTE",
                                                "events",
                                                "jobs.ready",
                                                null,
                                                new byte[0]);
                                return true;
                            }
                            return defaultValue(method.getReturnType());
                        });
        RecordingChannelPool pool = new RecordingChannelPool(channel);
        RabbitPublisher publisher = new RabbitPublisher(pool, "events", "jobs.ready", () -> {});

        assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> publisher.publish(message()).join());
        assertFalse(pool.lastHealthy);
    }

    @Test
    void kafkaShouldCompleteFromCallbackSetHeadersAndReleaseLease() {
        MockProducer<String, byte[]> producer =
                new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        AtomicInteger closed = new AtomicInteger();
        KafkaPublisher publisher =
                new KafkaPublisher(producer, "jobs-ready", closed::incrementAndGet);

        publisher.publish(message()).join();
        publisher.flush();
        publisher.close();
        publisher.close();

        ProducerRecord<String, byte[]> record = producer.history().getFirst();
        assertEquals("jobs-ready", record.topic());
        assertEquals("key-1", record.key());
        assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), record.value());
        assertEquals("application/json", header(record, "content-type"));
        assertEquals("gzip", header(record, "content-encoding"));
        assertFalse(producer.closed());
        assertEquals(1, closed.get());
    }

    private PushMessage message() {
        byte[] data = "payload".getBytes(StandardCharsets.UTF_8);
        return PushMessage.builder()
                .key("key-1")
                .serializedPayload(
                        SerializedPayload.builder()
                                .data(data)
                                .contentType("application/json")
                                .contentEncoding("gzip")
                                .originalSize(data.length)
                                .serializedSize(data.length)
                                .build())
                .compressed(true)
                .build();
    }

    private String header(ProducerRecord<String, byte[]> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private static final class RecordingChannelPool extends RabbitChannelPool {
        private final Channel channel;
        private final AtomicInteger releaseCount = new AtomicInteger();
        private volatile boolean lastHealthy;

        private RecordingChannelPool(Channel channel) {
            super(
                    proxy(
                            Connection.class,
                            (proxy, method, args) -> defaultValue(method.getReturnType())),
                    2);
            this.channel = channel;
        }

        @Override
        public Channel borrow() {
            return channel;
        }

        @Override
        public void release(Channel released, boolean healthy) {
            assertTrue(released == channel);
            lastHealthy = healthy;
            releaseCount.incrementAndGet();
        }

        @Override
        public boolean isPublisherConfirm() {
            return true;
        }
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(
                Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
