package com.ifielsc.ijob.core.resource.mq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Dedicated connection/channel: delivery tags never escape their owning session. */
public final class RabbitSourceSession implements MessageSourceSession {
    private final Connection connection;
    private final Channel channel;
    private final ArrayBlockingQueue<MessageDelivery> deliveries;
    private final AtomicLong bufferedBytes = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Throwable failure;

    public RabbitSourceSession(Connection connection, MessageSourceOptions options)
            throws Exception {
        this.connection = connection;
        deliveries = new ArrayBlockingQueue<>(options.prefetch());
        channel = connection.createChannel();
        try {
            channel.queueDeclarePassive(options.destination());
            channel.basicQos(options.prefetch());
            channel.basicConsume(
                    options.destination(),
                    false,
                    (tag, message) -> {
                        byte[] body = message.getBody();
                        if (body.length > options.maxMessageBytes()
                                || bufferedBytes.addAndGet(body.length)
                                        > options.maxBufferBytes()) {
                            failure =
                                    new IllegalArgumentException(
                                            "RabbitMQ message or buffer byte limit exceeded");
                            return;
                        }
                        var metadata = new LinkedHashMap<String, Object>();
                        var properties = message.getProperties();
                        metadata.put("queue", options.destination());
                        metadata.put("exchange", message.getEnvelope().getExchange());
                        metadata.put("routingKey", message.getEnvelope().getRoutingKey());
                        metadata.put("redelivered", message.getEnvelope().isRedeliver());
                        if (properties.getMessageId() != null)
                            metadata.put("messageId", properties.getMessageId());
                        if (properties.getTimestamp() != null)
                            metadata.put("timestamp", properties.getTimestamp().getTime());
                        if (properties.getHeaders() != null)
                            metadata.put("headers", headerValue(properties.getHeaders()));
                        if (!deliveries.offer(
                                new MessageDelivery(
                                        body,
                                        properties.getContentEncoding(),
                                        metadata,
                                        message.getEnvelope().getDeliveryTag())))
                            failure =
                                    new IllegalStateException("RabbitMQ delivery window exceeded");
                    },
                    tag -> failure = new IllegalStateException("RabbitMQ subscription cancelled"));
        } catch (Exception exception) {
            close();
            throw exception;
        }
    }

    private static Object headerValue(Object value) {
        if (value instanceof com.rabbitmq.client.LongString text) return text.toString();
        if (value instanceof byte[] bytes)
            return java.util.Base64.getEncoder().encodeToString(bytes);
        if (value instanceof java.util.Map<?, ?> map) {
            var copy = new LinkedHashMap<String, Object>();
            map.forEach((key, item) -> copy.put(String.valueOf(key), headerValue(item)));
            return copy;
        }
        if (value instanceof java.util.List<?> list)
            return list.stream().map(RabbitSourceSession::headerValue).toList();
        return value;
    }

    @Override
    public MessageDelivery poll(Duration timeout) {
        check();
        try {
            MessageDelivery result = deliveries.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            check();
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RabbitMQ read interrupted", exception);
        }
    }

    @Override
    public synchronized void confirm(List<MessageDelivery> batch) {
        check();
        try {
            for (MessageDelivery delivery : batch) {
                channel.basicAck((Long) delivery.receipt(), false);
                bufferedBytes.addAndGet(-delivery.body().length);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("RabbitMQ confirmation outcome is unknown", exception);
        }
    }

    private void check() {
        if (failure != null)
            throw new IllegalStateException("RabbitMQ consumption failed", failure);
        if (closed.get() || !channel.isOpen() || !connection.isOpen())
            throw new IllegalStateException("RabbitMQ consumer closed");
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) return;
        // Closing returns every unacknowledged delivery; no nack/requeue hot loop.
        try {
            connection.abort(5000);
        } catch (Exception exception) {
            failure = exception;
        }
    }
}
