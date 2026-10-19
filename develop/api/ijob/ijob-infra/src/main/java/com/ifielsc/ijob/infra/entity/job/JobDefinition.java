package com.ifielsc.ijob.infra.entity.job;

import com.ifielsc.ijob.infra.coordination.id.IdNamespace;
import com.ifielsc.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class JobDefinition {
    @AutoId(IdNamespace.JOB_DEFINITION)
    private Long id;

    private Long jobId;
    private Long revision;
    private String name;
    private Integer timeout;
    private Boolean enabled;
    private String description;
    private String layout;
    private LocalDateTime createTime;
}
