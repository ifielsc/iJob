package com.ifelio.ijob.contract.event;

import com.ifelio.ijob.contract.enums.TargetType;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EventBindingDTO {
    private Long id;
    private TargetType targetType;
    private Long targetId;
    private String eventCode;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
