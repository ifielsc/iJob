package com.ifielsc.ijob.contract.notify;

import com.ifielsc.ijob.contract.enums.NotifyTestSendStatus;
import com.ifielsc.ijob.contract.enums.NotifyType;

import lombok.Data;

@Data
public class NotifyTestSendDTO {
    private Long notifyConfigId;

    private NotifyType type;

    private NotifyTestSendStatus status;
}
