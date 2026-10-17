package com.ifelio.ijob.infra.entity.notify;

import com.ifelio.ijob.infra.coordination.id.IdNamespace;
import com.ifelio.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class MailChannel {
    @AutoId(IdNamespace.MAIL_CHANNEL)
    private Long id;

    private String name;
    private String config;
    private Boolean enabled;
    private Long revision;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
