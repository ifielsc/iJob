package com.ifelio.ijob.infra.entity.runtime;

import com.ifelio.ijob.contract.enums.LogLevel;
import com.ifelio.ijob.infra.coordination.id.IdNamespace;
import com.ifelio.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class InstanceLog {
    @AutoId(IdNamespace.INSTANCE_LOG)
    private Long id;

    private Long instanceId;
    private LocalDateTime logTime;
    private LogLevel level;
    private String content;
}
