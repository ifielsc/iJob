package com.ifielsc.ijob.plugin.push.publisher;

import com.ifielsc.ijob.core.resource.factory.mq.rabbit.RabbitChannelPool;
import com.ifielsc.ijob.plugin.push.PushMessage;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ReturnListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class RabbitPublisher implements PushPublisher {
    private static final long CONFIRM_TIMEOUT_MILLIS = 5000;

    private final RabbitChannelPool channelPool;

    /** exchange */
    private final String exchange;

    /** routingKey */
    private final String routingKey;

    private final AutoCloseable release;
    private final AtomicBoolean closed = new AtomicBoolean();

    public RabbitPublisher(
            RabbitChannelPool channelPool,
            String exchange,
            String routingKey,
            AutoCloseable release) {
        this.channelPool = channelPool;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.release = release;
    }

    @Override
    public CompletableFuture<Void> publish(PushMessage message) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Channel channel = null;
        boolean healthy = false;
        ReturnListener returnListener = null;
        AtomicBoolean returned = new AtomicBoolean();
        try {
            channel = channelPool.borrow();
            returnListener =
                    (replyCode,
                            replyText,
                            returnedExchange,
                            returnedRoutingKey,
                            returnedProperties,
                            returnedBody) -> returned.set(true);
            channel.addReturnListener(returnListener);
            channel.basicPublish(
                    exchange,
                    routingKey,
                    true,
                    buildProperties(message),
                    message.getSerializedPayload().getData());
            if (channelPool.isPublisherConfirm()
                    && !channel.waitForConfirms(CONFIRM_TIMEOUT_MILLIS)) {
                throw new IllegalStateException(
                        "rabbit publisher confirm was negatively acknowledged");
            }
            if (returned.get()) {
                throw new IllegalStateException("rabbit message was not routed");
            }

            healthy = true;
            future.complete(null);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            future.completeExceptionally(e);
        } finally {
            if (channel != null && returnListener != null) {
                channel.removeReturnListener(returnListener);
            }
            if (channel != null) {
                channelPool.release(channel, healthy);
            }
        }
        return future;
    }

    private BasicProperties buildProperties(PushMessage message) {
        BasicProperties.Builder builder = new BasicProperties.Builder();
        if (message.isCompressed()) {
            builder.contentEncoding("gzip");
        }
        if (message.getSerializedPayload() != null) {
            builder.contentType(message.getSerializedPayload().getContentType());
        }
        builder.deliveryMode(2);
        return builder.build();
    }

    @Override
    public void flush() {
        // Each publish waits for its own confirm when enabled.
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            closeRelease();
        }
    }

    private void closeRelease() {
        try {
            release.close();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("rabbit publisher close failed", e);
        }
    }
}
