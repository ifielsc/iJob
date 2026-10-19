// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.apicall;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/** Optional request on the same SERVICE resource; credentials remain resource-owned. */
@Getter
@Setter
public class RemoteStopConfig {
    private String method = "POST";
    private String path;
    private Map<String, String> headers;
    private Object body;
    private WebServiceCallConfig webService;
    private Integer timeoutSeconds = 5;
}
