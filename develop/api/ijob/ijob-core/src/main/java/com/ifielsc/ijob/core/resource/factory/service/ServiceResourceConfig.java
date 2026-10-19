package com.ifielsc.ijob.core.resource.factory.service;

import lombok.Data;
import lombok.ToString;

import java.util.Map;

@Data
@ToString(exclude = {"headers", "endpoint", "webService", "oauth2"})
public class ServiceResourceConfig {
    private ServiceProtocol protocol;
    private ServiceAuthType authType = ServiceAuthType.NONE;
    private Integer connectTimeout = 10;
    private Integer readTimeout = 30;
    private Integer writeTimeout = 30;
    private Integer callTimeout = 60;
    private Map<String, String> headers;

    /**
     * HTTP: https://xxx/api/user
     *
     * <p>WebService: http://xxx/service/UserService?wsdl
     */
    private String endpoint;

    private WebServiceConfig webService;

    private OAuth2ClientCredentialsConfig oauth2;
}
