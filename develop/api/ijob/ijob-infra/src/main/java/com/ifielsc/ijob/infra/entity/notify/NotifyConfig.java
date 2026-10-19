package com.ifielsc.ijob.infra.entity.notify;

import com.ifielsc.ijob.contract.enums.NotifyType;
import com.ifielsc.ijob.infra.coordination.id.IdNamespace;
import com.ifielsc.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class NotifyConfig {
    @AutoId(IdNamespace.NOTIFY_CONFIG)
    private Long id;

    private String name;
    private NotifyType type;
    private String config;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
