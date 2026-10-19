// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.resource;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

public abstract class ResourceConfigView {
    @Getter
    @Setter
    public static class Db extends ResourceConfigView {
        private String dbType;
        private String jdbcUrl;
        private String username;
        private boolean jdbcUrlConfigured;
        private boolean usernameConfigured;
        private boolean passwordConfigured;
        private String schema;
    }

    @Getter
    @Setter
    public static class File extends ResourceConfigView {
        private String host;
        private String username;
        private String basePath;
        private String endpoint;
        private String bucket;
        private String knownHosts;
        private String hostKeyVerification;
        private String hostKeyFingerprint;
        private String hostKeyType;
        private String protocol;
        private Integer port;
        private Integer timeout;
        private boolean hostConfigured;
        private boolean usernameConfigured;
        private boolean passwordConfigured;
        private boolean privateKeyConfigured;
        private boolean passphraseConfigured;
        private boolean knownHostsConfigured;
        private boolean basePathConfigured;
        private boolean endpointConfigured;
        private String region;
        private boolean bucketConfigured;
        private boolean accessKeyConfigured;
        private boolean secretKeyConfigured;
        private boolean sessionTokenConfigured;
        private boolean pathStyleAccess;
    }

    @Getter
    @Setter
    public static class Service extends ResourceConfigView {
        private String endpoint;
        private String wsdl;
        private String namespace;
        private String serviceName;
        private String portName;
        private String oauth2TokenEndpoint;
        private String oauth2ClientId;
        private String oauth2Scopes;
        private String oauth2Audience;
        private String protocol;
        private String authType;
        private Integer connectTimeout;
        private Integer readTimeout;
        private Integer writeTimeout;
        private Integer callTimeout;
        private boolean endpointConfigured;
        private Set<String> headerNames;
        private boolean webServiceConfigured;
        private boolean oauth2Configured;
        private boolean oauth2ClientIdConfigured;
        private boolean oauth2ClientSecretConfigured;
        private boolean oauth2TokenEndpointConfigured;
        private boolean oauth2ScopesConfigured;
        private boolean oauth2AudienceConfigured;
        private String oauth2ClientAuthenticationMethod;
        private Integer oauth2RefreshSkewSeconds;
    }

    @Getter
    @Setter
    public static class Rabbit extends ResourceConfigView {
        private String host;
        private String username;
        private String virtualHost;
        private String exchange;
        private String routingKey;
        private Integer port;
        private boolean publisherConfirm;
        private boolean hostConfigured;
        private boolean usernameConfigured;
        private boolean passwordConfigured;
        private boolean virtualHostConfigured;
        private boolean exchangeConfigured;
        private boolean routingKeyConfigured;
    }

    @Getter
    @Setter
    public static class Kafka extends ResourceConfigView {
        private String bootstrapServers;
        private String acks;
        private boolean bootstrapServersConfigured;
    }

    @Getter
    @Setter
    public static class Monitor extends ResourceConfigView {
        private String endpoint;
        private String protocol;
        private Integer connectTimeout;
        private Integer readTimeout;
        private String osType;
        private String agentVersion;
        private boolean endpointConfigured;
        private boolean tokenConfigured;
        private Set<String> headerNames;
    }

    @Getter
    @Setter
    public static class Ssh extends ResourceConfigView {
        private String hostKeyVerification;
        private String hostKeyFingerprint;
        private String hostKeyType;
        private String host;
        private String username;
        private String knownHosts;
        private Integer port;
        private Integer connectTimeout;
        private boolean hostConfigured;
        private boolean usernameConfigured;
        private boolean passwordConfigured;
        private boolean privateKeyConfigured;
        private boolean passphraseConfigured;
        private boolean knownHostsConfigured;
    }
}
