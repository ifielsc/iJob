package com.ifielsc.ijob.infra.mapper.task;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskIdentityRetirement {
    private Long taskId;
    private Long expectedDefinitionId;
    private Long expectedRevision;
    private LocalDateTime retiredTime;
}
