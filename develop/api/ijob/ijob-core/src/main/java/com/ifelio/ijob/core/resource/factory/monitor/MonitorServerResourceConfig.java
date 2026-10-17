package com.ifelio.ijob.core.resource.factory.monitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"endpoint", "token", "headers"})
public class MonitorServerResourceConfig {
    /** Agent endpoint, for example {@code http://localhost:9080}. */
    private String endpoint;

    private MonitorProtocol protocol;

    private String token;

    /** Connection timeout in milliseconds. */
    @Builder.Default private Integer connectTimeout = 5000;

    /** Read timeout in milliseconds. */
    @Builder.Default private Integer readTimeout = 10000;

    private com.ifelio.ijob.contract.monitor.OSType osType;

    /** Optional Agent version. */
    private String agentVersion;

    @Builder.Default private Map<String, String> headers = new LinkedHashMap<>();
}
