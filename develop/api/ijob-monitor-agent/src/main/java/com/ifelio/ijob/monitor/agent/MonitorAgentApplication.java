package com.ifelio.ijob.monitor.agent;

import com.ifelio.ijob.monitor.agent.config.AgentProperties;

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
