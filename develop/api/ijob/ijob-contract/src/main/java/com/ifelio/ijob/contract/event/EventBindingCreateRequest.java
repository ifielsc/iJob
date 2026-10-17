package com.ifelio.ijob.contract.event;

import com.ifelio.ijob.contract.enums.TargetType;

import lombok.Data;

@Data
public class EventBindingCreateRequest {
    private TargetType targetType;
    private Long targetId;
    private String eventCode;
    private Boolean enabled = true;
}
