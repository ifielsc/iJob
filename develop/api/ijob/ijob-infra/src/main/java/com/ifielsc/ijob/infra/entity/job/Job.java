package com.ifielsc.ijob.infra.entity.job;

import com.ifielsc.ijob.infra.coordination.id.IdNamespace;
import com.ifielsc.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Job {
    @AutoId(IdNamespace.JOB)
    private Long id;

    private String name;
    private Integer timeout;
    private Boolean enabled;
    private String description;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
