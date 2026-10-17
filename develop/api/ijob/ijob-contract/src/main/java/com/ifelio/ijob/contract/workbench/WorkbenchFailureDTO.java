package com.ifelio.ijob.contract.workbench;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Safe historical identity projection; no logs, variables or raw exception messages. */
@Getter
@Setter
public class WorkbenchFailureDTO {
    private String id;
    private String targetId;
    private String targetType;
    private String name;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime endTime;
}
