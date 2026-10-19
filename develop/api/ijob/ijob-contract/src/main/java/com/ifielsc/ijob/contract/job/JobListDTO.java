package com.ifielsc.ijob.contract.job;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class JobListDTO {
    private Long id;
    private Integer nodeCount;
    private String name;
    private Integer timeout;
    private Boolean enabled;
    private String description;
    private Long currentDefinitionId;
    private Long currentRevision;
    private List<String> groups;
    private LocalDateTime updateTime;
}
