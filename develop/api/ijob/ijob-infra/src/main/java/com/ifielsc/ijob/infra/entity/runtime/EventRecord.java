package com.ifielsc.ijob.infra.entity.runtime;

import com.ifielsc.ijob.contract.enums.EventSourceType;
import com.ifielsc.ijob.infra.coordination.id.IdNamespace;
import com.ifielsc.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class EventRecord {
    @AutoId(IdNamespace.EVENT)
    private Long id;

    private String eventCode;
    private EventSourceType sourceType;
    private String sourceKey;
    private String businessKey;
    private String correlationKey;
    private String idempotencyKey;
    private LocalDateTime occurredAt;
    private LocalDateTime arrivedAt;
    private String summary;
    private String payload;
    private String payloadDigest;
    private Integer payloadBytes;
    private LocalDateTime createTime;
}
