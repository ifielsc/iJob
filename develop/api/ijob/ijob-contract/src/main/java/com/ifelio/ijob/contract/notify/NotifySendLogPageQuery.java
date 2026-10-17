package com.ifelio.ijob.contract.notify;

import com.ifelio.ijob.contract.common.BasePageQuery;
import com.ifelio.ijob.contract.enums.NotifySendResult;
import com.ifelio.ijob.contract.enums.NotifySendSource;
import com.ifelio.ijob.contract.enums.TargetType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class NotifySendLogPageQuery extends BasePageQuery {
    private static final int MAX_PAGE_SIZE = 200;

    private NotifySendSource sourceType;
    private NotifySendResult result;
    private String deliveryStatus;
    private Long notifyConfigId;
    private TargetType targetType;
    private Long targetId;
    private Long instanceId;
    private LocalDateTime fromTime;
    private LocalDateTime toTime;

    @Override
    public Integer getPageSize() {
        return Math.min(super.getPageSize(), MAX_PAGE_SIZE);
    }
}
