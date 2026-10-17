package com.ifelio.ijob.contract.notify;

import lombok.Data;
import lombok.ToString;

@Data
public class NotifyTestSendRequest {
    @ToString.Exclude private String subject;

    @ToString.Exclude private String content;
}
