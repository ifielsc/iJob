package com.ifielsc.ijob.contract.concurrency;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConcurrencyResourceDTO {
    private Long id;
    private Long version;
    private String code;
    private String name;
    private Integer maxConcurrency;
    private Boolean enabled;
    private Integer referenceCount;
    private Integer activeInstanceCount;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
