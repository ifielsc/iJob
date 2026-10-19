// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.apicall;

import java.util.List;
import java.util.Map;

@lombok.Getter
@lombok.Setter
public class ApiCallConfig {
    public static final int DEFAULT_MAX_RESPONSE_BYTES = 1024 * 1024;

    private RemoteStopConfig remoteStop;
    private Long resourceId;
    private String path;
    private String method = "POST";
    private Map<String, String> headers;
    private Object body;
    private WebServiceCallConfig webService;
    private Integer maxResponseBytes = DEFAULT_MAX_RESPONSE_BYTES;

    private List<ResponseExtractor> extractors;

    private SuccessCondition successCondition;
}
