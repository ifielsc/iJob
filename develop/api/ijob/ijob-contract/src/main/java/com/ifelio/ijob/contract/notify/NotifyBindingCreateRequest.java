package com.ifelio.ijob.contract.notify;

import com.ifelio.ijob.contract.enums.NotifyPolicy;
import com.ifelio.ijob.contract.enums.TargetType;

import lombok.Data;

@Data
public class NotifyBindingCreateRequest {
    private TargetType targetType;
    private Long targetId;
    private Long notifyConfigId;
    private NotifyPolicy notifyPolicy;
}
