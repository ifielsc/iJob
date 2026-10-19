package com.ifielsc.ijob.infra.entity.concurrency;

import com.ifielsc.ijob.infra.coordination.id.IdNamespace;
import com.ifielsc.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ConcurrencyResource {
    @AutoId(IdNamespace.CONCURRENCY_RESOURCE)
    private Long id;

    private Long version;
    private String code;
    private String name;
    private Integer maxConcurrency;
    private Boolean enabled;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
