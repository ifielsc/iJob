package com.ifielsc.ijob.infra.mapper.job;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class JobCurrentDefinitionAdvance {
    private Long jobId;
    private Long expectedDefinitionId;
    private Long expectedRevision;
    private Long definitionId;
    private Long nextRevision;
    private String name;
    private Integer timeout;
    private Boolean enabled;
    private String description;
    private LocalDateTime updateTime;
}
