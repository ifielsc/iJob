package com.ifielsc.ijob.plugin.push.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.core.resource.ResourceClientLease;
import com.ifielsc.ijob.core.resource.ResourceRuntimeService;
import com.ifielsc.ijob.core.resource.factory.mq.kafka.KafkaClient;
import com.ifielsc.ijob.core.resource.factory.mq.rabbit.RabbitClient;
import com.ifielsc.ijob.plugin.push.publisher.PushPublisher;
import com.ifielsc.ijob.plugin.push.resolver.PushTaskPublishConfig;
import com.rabbitmq.client.Connection;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class PushPublisherFactoryTest {
    @Test
    void rabbitFactoryShouldLeaseDedicatedClientAndReleaseOnce() {
        RabbitClient client = new RabbitClient(proxyConnection(), 2);
        TrackingRuntime runtime = new TrackingRuntime(client);
        PushPublisher publisher =
                new RabbitPublisherFactory(runtime)
                        .create(
                                config(
                                        ResourceType.RABBITMQ,
                                        Map.of("exchange", "events", "routingKey", "jobs.ready")));

        publisher.close();
        publisher.close();

        assertEquals(1, runtime.leaseCalls.get());
        assertEquals(ResourceType.RABBITMQ, runtime.expectedType);
        assertEquals(1, runtime.releases.get());
    }

    @Test
    void kafkaFactoryShouldLeaseDedicatedClientAndReleaseOnce() {
        TrackingRuntime runtime = new TrackingRuntime(new KafkaClient(null));
        PushPublisher publisher =
                new KafkaPublisherFactory(runtime)
                        .create(config(ResourceType.KAFKA, Map.of("topic", "jobs-ready")));

        publisher.close();
        publisher.close();

        assertEquals(1, runtime.leaseCalls.get());
        assertEquals(ResourceType.KAFKA, runtime.expectedType);
        assertEquals(1, runtime.releases.get());
    }

    private PushTaskPublishConfig config(ResourceType type, Map<String, Object> properties) {
        PushTaskPublishConfig config = new PushTaskPublishConfig();
        config.setResourceId(12L);
        config.setType(type);
        config.setProperties(properties);
        return config;
    }

    private Connection proxyConnection() {
        return Connection.class.cast(
                Proxy.newProxyInstance(
                        Connection.class.getClassLoader(),
                        new Class<?>[] {Connection.class},
                        (proxy, method, args) -> {
                            if (method.getReturnType() == boolean.class) {
                                return false;
                            }
                            return null;
                        }));
    }

    private static final class TrackingRuntime extends ResourceRuntimeService {
        private final Object client;
        private final AtomicInteger leaseCalls = new AtomicInteger();
        private final AtomicInteger releases = new AtomicInteger();
        private ResourceType expectedType;

        private TrackingRuntime(Object client) {
            super(null, null, List.of());
            this.client = client;
        }

        @Override
        public <T> ResourceClientLease<T> leaseClient(
                Long resourceId, ResourceType type, Class<T> clientType) {
            assertEquals(12L, resourceId);
            leaseCalls.incrementAndGet();
            expectedType = type;
            return new ResourceClientLease<>(clientType.cast(client), releases::incrementAndGet);
        }
    }
}
