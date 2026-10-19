package com.ifielsc.ijob.core.resource.factory.mq.rabbit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class RabbitChannelPoolTest {
    @Test
    void shouldEnableConfirmValidateBorrowAndDestroyUnhealthyChannel() throws Exception {
        AtomicBoolean open = new AtomicBoolean(true);
        AtomicInteger confirmSelects = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        Channel channel =
                proxy(
                        Channel.class,
                        (ignored, method, args) ->
                                switch (method.getName()) {
                                    case "isOpen" -> open.get();
                                    case "confirmSelect" -> {
                                        confirmSelects.incrementAndGet();
                                        yield null;
                                    }
                                    case "close" -> {
                                        open.set(false);
                                        closes.incrementAndGet();
                                        yield null;
                                    }
                                    default -> defaultValue(method.getReturnType());
                                });
        Connection connection =
                proxy(
                        Connection.class,
                        (ignored, method, args) ->
                                "createChannel".equals(method.getName())
                                        ? channel
                                        : defaultValue(method.getReturnType()));
        RabbitChannelPool pool = new RabbitChannelPool(connection, 2, true);

        Channel borrowed = pool.borrow();
        pool.release(borrowed, true);
        Channel reused = pool.borrow();
        pool.release(reused, false);
        pool.close();

        assertSame(channel, borrowed);
        assertSame(channel, reused);
        assertEquals(1, confirmSelects.get());
        assertEquals(1, closes.get());
        assertTrue(pool.isPublisherConfirm());
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
