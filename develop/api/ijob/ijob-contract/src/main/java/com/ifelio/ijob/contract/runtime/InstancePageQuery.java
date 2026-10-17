package com.ifelio.ijob.contract.runtime;

import com.ifelio.ijob.contract.common.BasePageQuery;
import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.contract.enums.TargetType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class InstancePageQuery extends BasePageQuery {
    private TargetType targetType;
    private Long targetId;
    private Long triggerId;
    private InstanceStatus status;
    private Boolean rootOnly;
    private Long rootInstanceId;
    private LocalDateTime from;
    private LocalDateTime to;
    private String name;
}
