package com.ifielsc.ijob.infra.coordination.command;

import lombok.Data;

@Data
public class RuntimeCommandCandidate {
    private String requestId;
    private Long triggerId;
    private long fenceToken;
}
