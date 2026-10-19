package com.ifielsc.ijob.contract.runtime;

import com.ifielsc.ijob.contract.enums.RuntimeCommandStatus;
import com.ifielsc.ijob.contract.enums.RuntimeCommandType;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RuntimeCommandDTO {
    private String requestId;
    private boolean replayed;
    private Long instanceId;
    private Long rootInstanceId;
    private Long triggerId;
    private RuntimeCommandType commandType;
    private RuntimeCommandStatus status;
    private String requestedBy;
    private String resultMessage;
    private LocalDateTime expiresAt;
    private LocalDateTime processedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
