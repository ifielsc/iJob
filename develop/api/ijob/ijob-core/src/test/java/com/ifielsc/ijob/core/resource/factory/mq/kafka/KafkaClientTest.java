package com.ifielsc.ijob.core.resource.factory.mq.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.util.Properties;

class KafkaClientTest {
    @Test
    void closeShouldCloseProducerAndFactoryShouldBoundBlockingDefaults() {
        MockProducer<String, byte[]> producer =
                new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        KafkaClient client = new KafkaClient(producer);
        KafkaResourceConfig config = new KafkaResourceConfig();
        config.setBootstrapServers("127.0.0.1:9092");

        Properties properties = new KafkaResourceFactory().producerProperties(config);
        client.close();

        assertTrue(producer.closed());
        assertEquals(3, properties.get(ProducerConfig.RETRIES_CONFIG));
        assertEquals(30_000, properties.get(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG));
        assertEquals(60_000, properties.get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG));
        assertEquals(30_000, properties.get(ProducerConfig.MAX_BLOCK_MS_CONFIG));
    }
}
