// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.service;

import lombok.Data;

import java.util.Map;

@Data
public class ServiceResponse {
    private Integer status;
    private String body;
    private Map<String, String> headers;
    private Long elapsed;
}
