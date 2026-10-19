package com.ifielsc.ijob.infra.entity.runtime;

import com.ifielsc.ijob.contract.enums.LogLevel;
import com.ifielsc.ijob.infra.coordination.id.IdNamespace;
import com.ifielsc.ijob.infra.mybatis.AutoId;

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
