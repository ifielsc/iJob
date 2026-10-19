package com.ifielsc.ijob.contract.monitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentInfo {
    private String version;

    private String hostname;

    private OSType osType;

    private LocalDateTime startTime;
}
