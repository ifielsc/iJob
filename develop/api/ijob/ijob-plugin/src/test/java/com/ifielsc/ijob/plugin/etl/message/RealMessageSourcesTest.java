// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.ifielsc.ijob.core.resource.factory.mq.kafka.KafkaResourceFactory;
import com.ifielsc.ijob.core.resource.factory.mq.rabbit.RabbitResourceFactory;
import com.ifielsc.ijob.core.resource.mq.MessageDelivery;
import com.ifielsc.ijob.core.resource.mq.MessageSourceOptions;
import com.ifielsc.ijob.core.resource.mq.MessageSourceSession;
import com.ifielsc.ijob.infra.entity.resource.Resource;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@EnabledIfEnvironmentVariable(named = "IJOB_MQ_IT", matches = "true")
class RealMessageSourcesTest extends MessageTestSupport {
    RealMessageSourcesTest() throws Exception {}

    MessageSourceOptions options(String destination, String group) {
        return new MessageSourceOptions(destination, group, "EARLIEST", 2, 2, 1048576, 33554432);
    }

    @Test
    void kafkaCommitBoundaryAndReplayIntoDatabase() throws Exception {
        String topic = "ijob-qa-v1.etl-mq-" + UUID.randomUUID();
        String group = topic + "-group";
        String servers = System.getenv().getOrDefault("IJOB_KAFKA_SERVERS", "127.0.0.1:9092");
        var resource = new Resource();
        resource.setConfig(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(Map.of("bootstrapServers", servers)));
        var factory = new KafkaResourceFactory();
        var options = options(topic, group);
        try (AdminClient admin =
                AdminClient.create(
                        Map.of(
                                "bootstrap.servers",
                                servers,
                                "default.api.timeout.ms",
                                "10000",
                                "request.timeout.ms",
                                "10000"))) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1)))
                    .all()
                    .get(10, TimeUnit.SECONDS);
            try {
                try (var producer =
                        new KafkaProducer<String, byte[]>(
                                Map.of(
                                        "bootstrap.servers",
                                        servers,
                                        "key.serializer",
                                        "org.apache.kafka.common.serialization.StringSerializer",
                                        "value.serializer",
                                        "org.apache.kafka.common.serialization.ByteArraySerializer"))) {
                    for (String value : List.of("old", "new"))
                        producer.send(
                                        new ProducerRecord<>(
                                                topic,
                                                "key",
                                                ("{\"id\":1,\"value\":\"" + value + "\"}")
                                                        .getBytes(StandardCharsets.UTF_8)))
                                .get(10, TimeUnit.SECONDS);
                }
                // No target commit means no advancement; closing must permit replay.
                try (var source = factory.createMessageSource(resource, options)) {
                    assertNotNull(await(source));
                    var offsets =
                            admin.listConsumerGroupOffsets(group)
                                    .partitionsToOffsetAndMetadata()
                                    .get(10, TimeUnit.SECONDS);
                    assertEquals(0, offsets.get(new TopicPartition(topic, 0)).offset());
                }
                config.getSource().setTopic(topic);
                config.getSource().setGroupId(group);
                run(() -> factory.createMessageSource(resource, options), context());
                assertEquals(1, count());
                assertEquals("new", value());
                var offsets =
                        admin.listConsumerGroupOffsets(group)
                                .partitionsToOffsetAndMetadata()
                                .get(10, TimeUnit.SECONDS);
                assertEquals(2, offsets.get(new TopicPartition(topic, 0)).offset());
                try (var source = factory.createMessageSource(resource, options)) {
                    assertNull(source.poll(Duration.ofMillis(300)));
                }
            } finally {
                admin.deleteConsumerGroups(List.of(group)).all().get(15, TimeUnit.SECONDS);
                // Kafka's mapped-index rename on Windows can take the shared broker offline.
                // Retain this uniquely named synthetic fixture there for offline maintenance.
                if (System.getProperty("os.name", "").startsWith("Windows"))
                    System.out.println("Retained isolated Kafka fixture: " + topic);
                else admin.deleteTopics(List.of(topic)).all().get(15, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void rabbitUnconfirmedRequeuesThenCommitsAndAcknowledges() throws Exception {
        String queue = "ijob-qa-v1.etl-mq-" + UUID.randomUUID();
        var connectionFactory = new com.rabbitmq.client.ConnectionFactory();
        connectionFactory.setHost("127.0.0.1");
        connectionFactory.setPort(5672);
        connectionFactory.setUsername(System.getenv("IJOB_RABBIT_USER"));
        connectionFactory.setPassword(System.getenv("IJOB_RABBIT_PASSWORD"));
        connectionFactory.setVirtualHost(System.getenv("IJOB_RABBIT_VHOST"));
        var resource = new Resource();
        resource.setConfig(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(
                                Map.of(
                                        "host",
                                        "127.0.0.1",
                                        "port",
                                        5672,
                                        "username",
                                        System.getenv("IJOB_RABBIT_USER"),
                                        "password",
                                        System.getenv("IJOB_RABBIT_PASSWORD"),
                                        "virtualHost",
                                        System.getenv("IJOB_RABBIT_VHOST"))));
        var factory = new RabbitResourceFactory();
        var options = options(queue, null);
        try (var connection = connectionFactory.newConnection();
                var channel = connection.createChannel()) {
            channel.queueDeclare(queue, true, false, false, Map.of());
            channel.exchangeDeclare(queue, "direct", true);
            channel.queueBind(queue, queue, "test");
            try {
                for (String value : List.of("old", "new"))
                    channel.basicPublish(
                            queue,
                            "test",
                            null,
                            ("{\"id\":1,\"value\":\"" + value + "\"}")
                                    .getBytes(StandardCharsets.UTF_8));
                try (var source = factory.createMessageSource(resource, options)) {
                    assertNotNull(await(source));
                }
                config.getSource().setType(EtlTaskConfig.SourceType.RABBITMQ);
                config.getSource().setQueue(queue);
                config.getSource().setPrefetch(2);
                var context = context();
                run(() -> factory.createMessageSource(resource, options), context);
                assertEquals(1, count());
                assertEquals("new", value());
                assertTrue(
                        ((Number) context.getTaskContext().getVariable("etl.redeliveredMessages"))
                                        .longValue()
                                >= 1);
                assertNull(channel.basicGet(queue, true));
            } finally {
                try (var cleanup = connection.createChannel()) {
                    cleanup.queueDelete(queue);
                    cleanup.exchangeDelete(queue);
                }
            }
        }
    }

    private MessageDelivery await(MessageSourceSession source) throws Exception {
        long until = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < until) {
            var message = source.poll(Duration.ofMillis(100));
            if (message != null) return message;
        }
        fail("No isolated test message received");
        return null;
    }
}
