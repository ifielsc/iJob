package com.ifelio.ijob.core.resource.factory.monitor;

import cn.hutool.json.JSONUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.contract.enums.ResourceType;
import com.ifelio.ijob.core.resource.factory.ResourceClientFactory;
import com.ifelio.ijob.core.resource.factory.monitor.client.HttpMonitorClient;
import com.ifelio.ijob.infra.entity.resource.Resource;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MonitorServerResourceFactory implements ResourceClientFactory {
    private final ObjectMapper objectMapper;

    @Override
    public ResourceType supportType() {
        return ResourceType.MONITOR_SERVER;
    }

    @Override
    public Object create(Resource resource) {
        MonitorServerResourceConfig config =
                JSONUtil.toBean(resource.getConfig(), MonitorServerResourceConfig.class);

        return new HttpMonitorClient(config, objectMapper);
    }

    @Override
    public void verifyConnection(Object client) {
        if (!((com.ifelio.ijob.core.resource.factory.monitor.client.MonitorClient) client).ping()) {
            throw new IllegalStateException("monitor connection unavailable");
        }
    }
}
