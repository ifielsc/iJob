package com.ifelio.ijob.core.resource.factory.service;

import lombok.Data;

import java.util.Map;

@Data
public class ServiceRequest {
    @com.fasterxml.jackson.annotation.JsonIgnore private long deadlineNanos;
    @com.fasterxml.jackson.annotation.JsonIgnore private boolean controlRequest;
    private String path;
    private String method;
    private Map<String, String> headers;
    private Object body;
    private Integer maxResponseBytes;

    private WebServiceRequest webService;
}
