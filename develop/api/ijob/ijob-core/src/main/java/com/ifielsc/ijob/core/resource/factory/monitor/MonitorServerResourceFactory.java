// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.monitor;

import cn.hutool.json.JSONUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.core.resource.factory.ResourceClientFactory;
import com.ifielsc.ijob.core.resource.factory.monitor.client.HttpMonitorClient;
import com.ifielsc.ijob.infra.entity.resource.Resource;

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
        if (!((com.ifielsc.ijob.core.resource.factory.monitor.client.MonitorClient) client)
                .ping()) {
            throw new IllegalStateException("monitor connection unavailable");
        }
    }
}
