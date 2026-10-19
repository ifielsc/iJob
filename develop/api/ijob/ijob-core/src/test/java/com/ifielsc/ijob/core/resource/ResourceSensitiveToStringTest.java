// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.contract.resource.ResourceConfigRequest;
import com.ifielsc.ijob.contract.resource.ResourceUpsertRequest;
import com.ifielsc.ijob.core.resource.factory.db.DbResourceConfig;
import com.ifielsc.ijob.core.resource.factory.file.FileResourceConfig;
import com.ifielsc.ijob.core.resource.factory.monitor.MonitorServerResourceConfig;
import com.ifielsc.ijob.core.resource.factory.mq.kafka.KafkaResourceConfig;
import com.ifielsc.ijob.core.resource.factory.mq.rabbit.RabbitResourceConfig;
import com.ifielsc.ijob.core.resource.factory.service.ServiceResourceConfig;
import com.ifielsc.ijob.infra.entity.resource.Resource;

import org.junit.jupiter.api.Test;

import java.util.Map;

class ResourceSensitiveToStringTest {
    @Test
    void resourceAndAesConfigurationShouldRedactSecrets() {
        Resource resource = new Resource();
        resource.setConfig("entity-secret-config");

        AppProperties properties = new AppProperties();
        properties.getSecurity().getAes().setKey("aes-secret-key");

        assertAll(
                () -> assertHidden(resource, "entity-secret-config"),
                () -> assertHidden(properties, "aes-secret-key"));
    }

    @Test
    void typedResourceConfigurationsShouldNotRenderSensitiveFields() {
        DbResourceConfig db = new DbResourceConfig();
        db.setJdbcUrl("jdbc-secret-url");
        db.setUsername("db-secret-user");
        db.setPassword("db-secret-password");
        db.setSchema("db-secret-schema");

        FileResourceConfig file = new FileResourceConfig();
        file.setHost("file-secret-host");
        file.setUsername("file-secret-user");
        file.setPassword("file-secret-password");
        file.setPrivateKey("file-secret-private-key");
        file.setPassphrase("file-secret-passphrase");
        file.setKnownHosts("file-secret-known-hosts");
        file.setBasePath("file-secret-path");

        ServiceResourceConfig service = new ServiceResourceConfig();
        service.setEndpoint("service-secret-endpoint");
        service.setHeaders(Map.of("Authorization", "service-secret-token"));

        RabbitResourceConfig rabbit = new RabbitResourceConfig();
        rabbit.setHost("rabbit-secret-host");
        rabbit.setUsername("rabbit-secret-user");
        rabbit.setPassword("rabbit-secret-password");
        rabbit.setVirtualHost("rabbit-secret-vhost");
        rabbit.setExchange("rabbit-secret-exchange");
        rabbit.setRoutingKey("rabbit-secret-routing-key");

        KafkaResourceConfig kafka = new KafkaResourceConfig();
        kafka.setBootstrapServers("kafka-secret-bootstrap");

        MonitorServerResourceConfig monitor = new MonitorServerResourceConfig();
        monitor.setEndpoint("monitor-secret-endpoint");
        monitor.setToken("monitor-secret-token");
        monitor.setHeaders(Map.of("X-Api-Key", "monitor-secret-header"));

        assertAll(
                () ->
                        assertHidden(
                                db,
                                "jdbc-secret-url",
                                "db-secret-user",
                                "db-secret-password",
                                "db-secret-schema"),
                () ->
                        assertHidden(
                                file,
                                "file-secret-host",
                                "file-secret-user",
                                "file-secret-password",
                                "file-secret-private-key",
                                "file-secret-passphrase",
                                "file-secret-known-hosts",
                                "file-secret-path"),
                () -> assertHidden(service, "service-secret-endpoint", "service-secret-token"),
                () ->
                        assertHidden(
                                rabbit,
                                "rabbit-secret-host",
                                "rabbit-secret-user",
                                "rabbit-secret-password",
                                "rabbit-secret-vhost",
                                "rabbit-secret-exchange",
                                "rabbit-secret-routing-key"),
                () -> assertHidden(kafka, "kafka-secret-bootstrap"),
                () ->
                        assertHidden(
                                monitor,
                                "monitor-secret-endpoint",
                                "monitor-secret-token",
                                "monitor-secret-header"));
    }

    @Test
    void resourceWriteContractsShouldNotRenderConfigOrSecrets() {
        ResourceConfigRequest.Db config = new ResourceConfigRequest.Db();
        config.setJdbcUrl("jdbc-contract-secret");
        config.setUsername("contract-secret-user");
        config.setPassword("contract-secret-password");

        ResourceUpsertRequest request = new ResourceUpsertRequest();
        request.setCode("demo-db");
        request.setType(ResourceType.DB);
        request.setDbConfig(config);

        ResourceConfigRequest.File file = new ResourceConfigRequest.File();
        file.setHost("contract-file-host");
        file.setUsername("contract-file-user");
        file.setPassword("contract-file-password");
        file.setPrivateKey("contract-file-key");
        file.setPassphrase("contract-file-passphrase");
        file.setKnownHosts("contract-file-known-hosts");
        file.setBasePath("contract-file-path");

        ResourceConfigRequest.Service service = new ResourceConfigRequest.Service();
        service.setEndpoint("contract-service-endpoint");
        service.setHeaders(Map.of("Authorization", "contract-service-token"));

        ResourceConfigRequest.Rabbit rabbit = new ResourceConfigRequest.Rabbit();
        rabbit.setHost("contract-rabbit-host");
        rabbit.setUsername("contract-rabbit-user");
        rabbit.setPassword("contract-rabbit-password");

        ResourceConfigRequest.Kafka kafka = new ResourceConfigRequest.Kafka();
        kafka.setBootstrapServers("contract-kafka-bootstrap");

        ResourceConfigRequest.Monitor monitor = new ResourceConfigRequest.Monitor();
        monitor.setEndpoint("contract-monitor-endpoint");
        monitor.setToken("contract-monitor-token");
        monitor.setHeaders(Map.of("X-Api-Key", "contract-monitor-header"));

        assertAll(
                () ->
                        assertHidden(
                                config,
                                "jdbc-contract-secret",
                                "contract-secret-user",
                                "contract-secret-password"),
                () ->
                        assertHidden(
                                file,
                                "contract-file-host",
                                "contract-file-user",
                                "contract-file-password",
                                "contract-file-key",
                                "contract-file-passphrase",
                                "contract-file-known-hosts",
                                "contract-file-path"),
                () -> assertHidden(service, "contract-service-endpoint", "contract-service-token"),
                () ->
                        assertHidden(
                                rabbit,
                                "contract-rabbit-host",
                                "contract-rabbit-user",
                                "contract-rabbit-password"),
                () -> assertHidden(kafka, "contract-kafka-bootstrap"),
                () ->
                        assertHidden(
                                monitor,
                                "contract-monitor-endpoint",
                                "contract-monitor-token",
                                "contract-monitor-header"),
                () ->
                        assertHidden(
                                request,
                                "jdbc-contract-secret",
                                "contract-secret-user",
                                "contract-secret-password"));
    }

    private void assertHidden(Object value, String... secrets) {
        String rendered = value.toString();
        for (String secret : secrets) {
            assertFalse(
                    rendered.contains(secret),
                    () -> "sensitive value was rendered by " + value.getClass().getSimpleName());
        }
    }
}
