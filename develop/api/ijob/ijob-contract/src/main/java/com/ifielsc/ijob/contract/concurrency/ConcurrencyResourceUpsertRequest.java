package com.ifielsc.ijob.contract.concurrency;

import lombok.Data;

@Data
public class ConcurrencyResourceUpsertRequest {
    private Long expectedVersion;
    private String code;
    private String name;
    private Integer maxConcurrency;
    private Boolean enabled;
    private String description;
}
