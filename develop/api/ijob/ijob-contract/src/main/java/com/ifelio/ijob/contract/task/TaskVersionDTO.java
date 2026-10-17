package com.ifelio.ijob.contract.task;

import com.ifelio.ijob.contract.enums.PluginType;

import lombok.Data;

import java.time.LocalDateTime;

/** Public version metadata; never contains plugin configuration. */
@Data
public class TaskVersionDTO {
    private Long taskId;
    private Long definitionId;
    private Long revision;
    private String name;
    private PluginType pluginType;
    private Boolean enabled;
    private LocalDateTime publishedAt;
    private boolean current;
}
