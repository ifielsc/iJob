package com.ifielsc.ijob.contract.notify;

import com.ifielsc.ijob.contract.enums.NotifyPolicy;
import com.ifielsc.ijob.contract.enums.NotifyType;
import com.ifielsc.ijob.contract.enums.TargetType;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotifyBindingDTO {
    private Long id;
    private TargetType targetType;
    private Long targetId;
    private Long notifyConfigId;
    private String notifyConfigName;
    private NotifyType notifyType;
    private Boolean notifyConfigEnabled;
    private NotifyPolicy notifyPolicy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
