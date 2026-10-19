package com.ifielsc.ijob.core.resource.factory.mq.kafka;

import cn.hutool.json.JSONUtil;

import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.core.resource.factory.ResourceClientFactory;
import com.ifielsc.ijob.infra.entity.resource.Resource;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Component
public class KafkaResourceFactory implements ResourceClientFactory {
    @Override
    public ResourceType supportType() {
        return ResourceType.KAFKA;
    }

    @Override
    public Object create(Resource resource) {
        try {
            KafkaResourceConfig config =
                    JSONUtil.toBean(resource.getConfig(), KafkaResourceConfig.class);
            Properties props = producerProperties(config);
            KafkaProducer<String, byte[]> producer = new KafkaProducer<>(props);
            return new KafkaClient(producer);
        } catch (Exception e) {
            throw new IllegalStateException("Kafka client creation failed", e);
        }
    }

    @Override
    public void testConnection(Resource resource) throws Exception {
        KafkaResourceConfig config =
                JSONUtil.toBean(resource.getConfig(), KafkaResourceConfig.class);
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5000);
        try (AdminClient admin = AdminClient.create(props)) {
            admin.describeCluster().clusterId().get(5, TimeUnit.SECONDS);
        }
    }

    @Override
    public void close(Object client) {
        ((KafkaClient) client).close();
    }

    @Override
    public com.ifielsc.ijob.core.resource.mq.MessageSourceSession createMessageSource(
            Resource resource, com.ifielsc.ijob.core.resource.mq.MessageSourceOptions options) {
        KafkaResourceConfig config =
                JSONUtil.toBean(resource.getConfig(), KafkaResourceConfig.class);
        Properties props = new Properties();
        props.put("bootstrap.servers", config.getBootstrapServers());
        props.put("group.id", options.groupId());
        props.put(
                "key.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        props.put(
                "value.deserializer",
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        props.put("enable.auto.commit", false);
        props.put("allow.auto.create.topics", false);
        props.put("auto.offset.reset", "none");
        props.put("isolation.level", "read_committed");
        props.put("max.poll.records", options.batchSize());
        props.put("max.partition.fetch.bytes", options.maxMessageBytes());
        props.put("fetch.max.bytes", (int) Math.min(Integer.MAX_VALUE, options.maxBufferBytes()));
        props.put("default.api.timeout.ms", 10000);
        props.put("request.timeout.ms", 10000);
        return new com.ifielsc.ijob.core.resource.mq.KafkaSourceSession(
                options, () -> new org.apache.kafka.clients.consumer.KafkaConsumer<>(props));
    }

    Properties producerProperties(KafkaResourceConfig config) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        props.put(ProducerConfig.ACKS_CONFIG, config.getAcks());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, config.getKeySerializer());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, config.getValueSerializer());
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30_000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 60_000);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 30_000);
        return props;
    }
}
