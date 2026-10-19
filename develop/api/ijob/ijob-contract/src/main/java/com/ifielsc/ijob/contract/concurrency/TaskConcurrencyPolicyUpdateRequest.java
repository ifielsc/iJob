package com.ifielsc.ijob.contract.concurrency;

import lombok.Data;

import java.util.List;

@Data
public class TaskConcurrencyPolicyUpdateRequest {
    private Long expectedVersion;
    private Integer taskMaxConcurrency;
    private List<Long> resourceIds;
}
