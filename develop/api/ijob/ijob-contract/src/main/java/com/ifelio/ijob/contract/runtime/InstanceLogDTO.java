package com.ifelio.ijob.contract.runtime;

import com.ifelio.ijob.contract.enums.LogLevel;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InstanceLogDTO {
    private Long id;
    private Long instanceId;
    private LocalDateTime logTime;
    private LogLevel level;
    private String content;
}
