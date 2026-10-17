package com.ifelio.ijob.contract.notify;

import com.ifelio.ijob.contract.enums.NotifyType;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotifyConfigDTO {
    private Long id;
    private String name;
    private NotifyType type;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private NotifyConfigView config;
}
