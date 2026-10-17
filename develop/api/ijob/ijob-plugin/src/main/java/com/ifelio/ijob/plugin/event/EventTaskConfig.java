package com.ifelio.ijob.plugin.event;

import com.ifelio.ijob.contract.enums.EventAction;

import lombok.Data;

import java.util.List;

@Data
public class EventTaskConfig {
    private EventAction action = EventAction.PUBLISH;
    private String eventCode;
    private String businessKey;
    private String correlationKey;
    private String idempotencyKey;
    private String occurredAt;
    private String summary;
    private Object payload;
    private boolean suppressSameInbound = true;

    private Integer timeoutSeconds;
    private Integer lookbackSeconds = 0;
    private List<String> allowedOperatorIds;

    private Long fileResourceId;
    private String filePath;
    private String filePattern = "*";
    private boolean recursive;
    private Integer minStableAgeSeconds = 30;
    private Integer maxMatches = 100;
    private Boolean fileCaseSensitive = true;
    private Integer maxDepth = 16;
    private Integer maxScannedEntries = 100_000;
    private Integer scanTimeoutSeconds = 60;
}
