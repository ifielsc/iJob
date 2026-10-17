package com.ifelio.ijob.contract.resource;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Map;

public final class ResourceConfigRequest {
    private ResourceConfigRequest() {}

    @Getter
    @Setter
    @ToString(exclude = {"jdbcUrl", "username", "password", "schema"})
    public static class Db {
        private Boolean preserveExisting;
        private String dbType;
        private String jdbcUrl;
        private String username;
        private String password;
        private Boolean clearPassword;
        private String schema;
    }

    @Getter
    @Setter
    @ToString(
            exclude = {
                "host", "username", "password", "privateKey", "passphrase",
                "knownHosts", "basePath", "endpoint", "accessKey", "secretKey",
                "sessionToken", "bucket"
            })
    public static class File {
        private Boolean preserveExisting;
        private String hostKeyVerification;
        private String hostKey;
        private String protocol;
        private String host;
        private Integer port;
        private String username;
        private String password;
        private Boolean clearPassword;
        private String privateKey;
        private Boolean clearPrivateKey;
        private String passphrase;
        private Boolean clearPassphrase;
        private String knownHosts;
        private String basePath;
        private Integer timeout;
        private String endpoint;
        private String region;
        private String bucket;
        private String accessKey;
        private String secretKey;
        private Boolean clearSecretKey;
        private String sessionToken;
        private Boolean clearSessionToken;
        private Boolean pathStyleAccess;
    }

    @Getter
    @Setter
    @ToString(exclude = {"headers", "endpoint", "webService", "oauth2"})
    public static class Service {
        private Boolean preserveExisting;
        private List<String> preservedHeaderNames;
        private String protocol;
        private String authType;
        private Integer connectTimeout;
        private Integer readTimeout;
        private Integer writeTimeout;
        private Integer callTimeout;
        private Map<String, String> headers;
        private String endpoint;
        private WebService webService;
        private OAuth2ClientCredentials oauth2;
    }

    @Getter
    @Setter
    @ToString(exclude = {"tokenEndpoint", "clientId", "clientSecret", "audience"})
    public static class OAuth2ClientCredentials {
        private Boolean clearScopes;
        private Boolean clearAudience;
        private String tokenEndpoint;
        private String clientId;
        private String clientSecret;
        private Boolean clearClientSecret;
        private String scopes;
        private String audience;
        private String clientAuthenticationMethod;
        private Integer refreshSkewSeconds;
    }

    @Getter
    @Setter
    @ToString(exclude = {"wsdl", "namespace", "serviceName", "portName"})
    public static class WebService {
        private String wsdl;
        private String namespace;
        private String serviceName;
        private String portName;
    }

    @Getter
    @Setter
    @ToString(exclude = {"host", "username", "password", "virtualHost", "exchange", "routingKey"})
    public static class Rabbit {
        private Boolean preserveExisting;
        private String host;
        private Integer port;
        private String username;
        private String password;
        private Boolean clearPassword;
        private String virtualHost;
        private String exchange;
        private String routingKey;
        private Boolean publisherConfirm;
    }

    @Getter
    @Setter
    @ToString(exclude = "bootstrapServers")
    public static class Kafka {
        private Boolean preserveExisting;
        private String bootstrapServers;
        private String acks;
    }

    @Getter
    @Setter
    @ToString(exclude = {"endpoint", "token", "headers"})
    public static class Monitor {
        private Boolean preserveExisting;
        private List<String> preservedHeaderNames;
        private String endpoint;
        private String protocol;
        private String token;
        private Boolean clearToken;
        private Integer connectTimeout;
        private Integer readTimeout;
        private String osType;
        private String agentVersion;
        private Map<String, String> headers;
    }

    @Getter
    @Setter
    @ToString(exclude = {"host", "username", "password", "privateKey", "passphrase", "knownHosts"})
    public static class Ssh {
        private String hostKeyVerification;
        private String hostKey;
        private Boolean preserveExisting;
        private String host;
        private Integer port;
        private String username;
        private String password;
        private Boolean clearPassword;
        private String privateKey;
        private Boolean clearPrivateKey;
        private String passphrase;
        private Boolean clearPassphrase;
        private String knownHosts;
        private Integer connectTimeout;
    }
}
