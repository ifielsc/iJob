// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.apicall;

import lombok.Data;

import java.util.Map;

@Data
public class WebServiceCallConfig {
    /** operation */
    private String operation;

    /** SOAPAction */
    private String action;

    private Map<String, Object> parameters;
}
