package com.ifielsc.ijob.monitor.agent.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
@Getter
@Setter
public class AgentProperties {

    /** Agent authentication token. */
    private String token;

    /** Hostname reported by the Agent. Automatically detected when omitted. */
    private String hostname;

    /** Maximum collection duration in milliseconds. */
    private Integer collectTimeout = 10000;

    /** Maximum number of collection requests running concurrently. */
    private Integer collectConcurrency = 2;

    /** Maximum number of collection requests waiting for execution. */
    private Integer collectQueueCapacity = 4;
}
