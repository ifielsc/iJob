package com.ifelio.ijob.core.resource.factory.service;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(exclude = {"tokenEndpoint", "clientId", "clientSecret", "audience"})
public class OAuth2ClientCredentialsConfig {
    private String tokenEndpoint;
    private String clientId;
    private String clientSecret;
    private String scopes;
    private String audience;
    private OAuth2ClientAuthenticationMethod clientAuthenticationMethod =
            OAuth2ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
    private int refreshSkewSeconds = 30;
}
