package com.ifielsc.ijob.monitor.agent.model;

import com.ifielsc.ijob.contract.monitor.AgentInfo;
import com.ifielsc.ijob.contract.monitor.OSType;
import com.ifielsc.ijob.monitor.agent.config.AgentProperties;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

@Component
public class AgentIdentity {

    private static final String VERSION = "1.0.0";

    private final String hostname;
    private final OSType osType;

    public AgentIdentity(AgentProperties properties) {
        hostname = resolveHostname(properties.getHostname());
        osType = resolveOsType(System.getProperty("os.name"));
    }

    public AgentInfo info(AgentRuntime runtime) {
        return AgentInfo.builder()
                .version(VERSION)
                .hostname(hostname)
                .osType(osType)
                .startTime(runtime.getStartTime())
                .build();
    }

    static OSType resolveOsType(String osName) {
        String normalized = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (normalized.contains("win")) {
            return OSType.WINDOWS;
        }
        if (normalized.contains("mac") || normalized.contains("darwin")) {
            return OSType.MACOS;
        }
        if (normalized.contains("linux")) {
            return OSType.LINUX;
        }
        throw new IllegalStateException("unsupported agent operating system");
    }

    private String resolveHostname(String configured) {
        if (configured != null && !configured.isBlank()) {
            String normalized = configured.trim();
            if (!configured.equals(normalized) || normalized.length() > 255) {
                throw new IllegalStateException("agent hostname is invalid");
            }
            return normalized;
        }
        try {
            String detected = InetAddress.getLocalHost().getHostName();
            if (detected == null || detected.isBlank() || detected.length() > 255) {
                throw new IllegalStateException("agent hostname could not be detected");
            }
            return detected;
        } catch (UnknownHostException exception) {
            throw new IllegalStateException("agent hostname could not be detected", exception);
        }
    }
}
