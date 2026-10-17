package com.ifelio.ijob.contract.notify;

import com.ifelio.ijob.contract.enums.NotifyTestSendStatus;
import com.ifelio.ijob.contract.enums.NotifyType;

import lombok.Data;

@Data
public class NotifyTestSendDTO {
    private Long notifyConfigId;

    private NotifyType type;

    private NotifyTestSendStatus status;
}
