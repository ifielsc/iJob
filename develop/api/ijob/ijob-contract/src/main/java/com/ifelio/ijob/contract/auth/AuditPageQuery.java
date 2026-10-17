package com.ifelio.ijob.contract.auth;

import lombok.Getter;
import lombok.Setter;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Getter
@Setter
public class AuditPageQuery {
    private int pageNum = 1;
    private int pageSize = 20;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime to;

    private String actorLoginId;
    private String actionCode;
    private String targetType;
    private String targetId;
    private String resultCode;
    private String sortBy = "occurredAt";
    private String sortDirection = "desc";
}
