package com.ifielsc.ijob.core.resource.factory.mq.kafka;

import lombok.Getter;

import org.apache.kafka.clients.producer.Producer;

import java.time.Duration;

@Getter
public class KafkaClient {
    private final Producer<String, byte[]> producer;

    public KafkaClient(Producer<String, byte[]> producer) {
        this.producer = producer;
    }

    public void close() {
        producer.close(Duration.ofSeconds(5));
    }
}
