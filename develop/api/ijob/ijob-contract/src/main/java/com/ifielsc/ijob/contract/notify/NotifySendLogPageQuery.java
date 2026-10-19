package com.ifielsc.ijob.contract.notify;

import com.ifielsc.ijob.contract.common.BasePageQuery;
import com.ifielsc.ijob.contract.enums.NotifySendResult;
import com.ifielsc.ijob.contract.enums.NotifySendSource;
import com.ifielsc.ijob.contract.enums.TargetType;

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
