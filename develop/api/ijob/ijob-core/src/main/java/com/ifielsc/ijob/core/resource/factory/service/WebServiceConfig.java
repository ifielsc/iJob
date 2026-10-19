// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.service;

import lombok.Data;

@Data
public class WebServiceConfig {
    private String wsdl;

    /** targetNamespace */
    private String namespace;

    private String serviceName;

    private String portName;
}
