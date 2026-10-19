package com.ifielsc.ijob.contract.event;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EventPublishRequest {
    private String sourceKey;
    private String eventCode;
    private String businessKey;
    private String correlationKey;
    private String idempotencyKey;
    private LocalDateTime occurredAt;
    private String summary;
    private Object payload;
}
