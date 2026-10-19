// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.push;

import com.ifielsc.ijob.plugin.push.resolver.PushTaskPublishConfig;

import lombok.Data;

@Data
public class PushTaskConfig {
    public static final int DEFAULT_MAX_MESSAGE_BYTES = 1024 * 1024;
    public static final long DEFAULT_MAX_MESSAGES = 1_000_000L;
    public static final long DEFAULT_MAX_TOTAL_PAYLOAD_BYTES = 1024L * 1024 * 1024;

    /** Source database resource ID. */
    private Long sourceResourceId;

    private String sql;

    private PushTaskPublishConfig target;

    private String serializer = "json";

    private String compression = "none";

    private Integer fetchSize = 500;

    private Integer batchSize = 500;

    private Integer maxInflight = 500;

    private Integer publishTimeoutSeconds = 60;

    private Integer retryTimes = 3;

    private Integer maxMessageBytes = DEFAULT_MAX_MESSAGE_BYTES;

    private Long maxMessages = DEFAULT_MAX_MESSAGES;

    private Long maxTotalPayloadBytes = DEFAULT_MAX_TOTAL_PAYLOAD_BYTES;
}
