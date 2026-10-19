package com.ifielsc.ijob.plugin.push;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.plugin.push.resolver.PushTaskPublishConfig;

import org.junit.jupiter.api.Test;

import java.util.Map;

class PushTaskConfigValidatorTest {
    private final PushTaskConfigValidator validator = new PushTaskConfigValidator();

    @Test
    void rabbitAndKafkaConfigsShouldBeAccepted() {
        PushTaskConfig rabbit = base();
        rabbit.setTarget(
                target(ResourceType.RABBITMQ, Map.of("exchange", "", "routingKey", "jobs.ready")));
        PushTaskConfig kafka = base();
        kafka.setSql("WITH source AS (SELECT 1) SELECT * FROM source");
        kafka.setCompression("gzip");
        kafka.setTarget(target(ResourceType.KAFKA, Map.of("topic", "jobs.ready-v1")));

        assertDoesNotThrow(() -> validator.validate(rabbit));
        assertDoesNotThrow(() -> validator.validate(kafka));
    }

    @Test
    void sourceAndReadQueryAreRequired() {
        PushTaskConfig config = base();
        config.setSourceResourceId(0L);
        assertInvalid(config);

        config = base();
        config.setSql("delete from orders");
        assertInvalid(config);
    }

    @Test
    void targetTypeAndTypeSpecificPropertiesAreRequired() {
        PushTaskConfig config = base();
        config.setTarget(target(ResourceType.DB, Map.of()));
        assertInvalid(config);

        config = base();
        config.setTarget(
                target(ResourceType.RABBITMQ, Map.of("exchange", "events", "routingKey", " ")));
        assertInvalid(config);

        config = base();
        config.setTarget(target(ResourceType.KAFKA, Map.of("topic", "bad topic")));
        assertInvalid(config);
    }

    @Test
    void codecAndBoundedExecutionValuesAreValidated() {
        PushTaskConfig config = base();
        config.setCompression("zip");
        assertInvalid(config);

        config = base();
        config.setBatchSize(0);
        assertInvalid(config);

        config = base();
        config.setMaxInflight(10_001);
        assertInvalid(config);

        config = base();
        config.setRetryTimes(11);
        assertInvalid(config);

        config = base();
        config.setMaxMessageBytes(1023);
        assertInvalid(config);

        config = base();
        config.setMaxTotalPayloadBytes(1024L);
        assertInvalid(config);
    }

    @Test
    void commentedQueryShouldPassButMutatingCteShouldFail() {
        PushTaskConfig commented = base();
        commented.setSql("-- report\n(select id from orders)");
        assertDoesNotThrow(() -> validator.validate(commented));

        PushTaskConfig mutating = base();
        mutating.setSql(
                "with removed as (delete from orders returning *) " + "select * from removed");
        assertInvalid(mutating);
    }

    private PushTaskConfig base() {
        PushTaskConfig config = new PushTaskConfig();
        config.setSourceResourceId(11L);
        config.setSql("SELECT id FROM orders");
        config.setTarget(target(ResourceType.KAFKA, Map.of("topic", "jobs-ready")));
        return config;
    }

    private void assertInvalid(PushTaskConfig config) {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
    }

    private PushTaskPublishConfig target(ResourceType type, Map<String, Object> properties) {
        PushTaskPublishConfig target = new PushTaskPublishConfig();
        target.setResourceId(12L);
        target.setType(type);
        target.setProperties(properties);
        return target;
    }
}
