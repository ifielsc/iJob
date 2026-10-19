package com.ifielsc.ijob.core.resource.factory.service;

import lombok.Data;

import java.util.Map;

@Data
public class WebServiceRequest {
    /** ws operation */
    private String operation;

    /** SOAPAction */
    private String action;

    private Map<String, Object> parameters;
}
