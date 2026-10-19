package com.ifielsc.ijob.core.resource.factory.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.core.resource.factory.monitor.client.HttpMonitorClient;
import com.ifielsc.ijob.infra.entity.resource.Resource;

import org.junit.jupiter.api.Test;

class MonitorServerResourceFactoryTest {
    private final MonitorServerResourceFactory factory =
            new MonitorServerResourceFactory(new ObjectMapper());

    @Test
    void shouldSupportMonitorServerResources() {
        assertEquals(ResourceType.MONITOR_SERVER, factory.supportType());
    }

    @Test
    void shouldCreateHttpClientFromResourceConfig() {
        Resource resource = new Resource();
        resource.setType(ResourceType.MONITOR_SERVER);
        resource.setConfig(
                """
                {
                  "endpoint": "http://127.0.0.1:9080",
                  "protocol": "HTTP",
                  "connectTimeout": 500,
                  "readTimeout": 1000
                }
                """);

        Object client = factory.create(resource);

        assertInstanceOf(HttpMonitorClient.class, client);
    }
}
