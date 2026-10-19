// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent;

import com.ifielsc.ijob.monitor.agent.config.AgentProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AgentProperties.class)
public class MonitorAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonitorAgentApplication.class, args);
    }
}
