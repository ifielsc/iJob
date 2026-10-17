package com.ifelio.ijob.contract.notify;

import com.ifelio.ijob.contract.enums.NotifyType;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotifyConfigListDTO {
    private Long id;
    private String name;
    private NotifyType type;
    private Boolean enabled;
    private LocalDateTime updateTime;
}
