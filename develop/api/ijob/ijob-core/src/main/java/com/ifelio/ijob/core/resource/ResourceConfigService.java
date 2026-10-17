package com.ifelio.ijob.core.resource;

import cn.hutool.json.JSONUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.MessageDefinition;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.ResourceType;
import com.ifelio.ijob.contract.monitor.OSType;
import com.ifelio.ijob.contract.resource.ResourceConfigRequest;
import com.ifelio.ijob.contract.resource.ResourceConfigView;
import com.ifelio.ijob.contract.resource.ResourceUpsertRequest;
import com.ifelio.ijob.core.error.CoreMessage;
import com.ifelio.ijob.core.resource.factory.db.DbResourceConfig;
import com.ifelio.ijob.core.resource.factory.db.DbType;
import com.ifelio.ijob.core.resource.factory.file.FileProtocol;
import com.ifelio.ijob.core.resource.factory.file.FileResourceConfig;
import com.ifelio.ijob.core.resource.factory.monitor.MonitorProtocol;
import com.ifelio.ijob.core.resource.factory.monitor.MonitorServerResourceConfig;
import com.ifelio.ijob.core.resource.factory.mq.kafka.KafkaResourceConfig;
import com.ifelio.ijob.core.resource.factory.mq.rabbit.RabbitResourceConfig;
import com.ifelio.ijob.core.resource.factory.service.OAuth2ClientAuthenticationMethod;
import com.ifelio.ijob.core.resource.factory.service.OAuth2ClientCredentialsConfig;
import com.ifelio.ijob.core.resource.factory.service.ServiceAuthType;
import com.ifelio.ijob.core.resource.factory.service.ServiceProtocol;
import com.ifelio.ijob.core.resource.factory.service.ServiceResourceConfig;
import com.ifelio.ijob.core.resource.factory.service.WebServiceConfig;
import com.ifelio.ijob.core.resource.factory.ssh.SshHostKeys;
import com.ifelio.ijob.core.resource.factory.ssh.SshResourceConfig;
import com.ifelio.ijob.core.resource.security.ResourceConfigCipher;
import com.ifelio.ijob.infra.entity.resource.Resource;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

@Service
public class ResourceConfigService {
    private static final int MAX_ENDPOINT_LENGTH = 2048;
    private static final int MAX_SECRET_LENGTH = 65535;
    private static final int MAX_HEADER_COUNT = 50;
    private static final int MAX_HEADER_VALUE_LENGTH = 4096;
    private static final Pattern HEADER_NAME_PATTERN =
            Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,128}");
    private static final Pattern SENSITIVE_QUERY_PATTERN =
            Pattern.compile("(?i)(?:^|[&;])(token|key|secret|password|passwd|pwd)=");
    private static final Pattern SENSITIVE_JDBC_PATTERN =
            Pattern.compile("(?i)(?:^|[?&;])(password|passwd|pwd)=");
    private static final Pattern BUCKET_PATTERN =
            Pattern.compile("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]");

    private final ResourceConfigCipher cipher;

    public ResourceConfigService(ResourceConfigCipher cipher) {
        this.cipher = cipher;
    }

    public String protect(ResourceUpsertRequest request, Resource existing) {
        requireMatchingConfig(request);
        String plaintext =
                switch (request.getType()) {
                    case DB ->
                            JSONUtil.toJsonStr(
                                    buildDb(
                                            request.getDbConfig(),
                                            previous(
                                                    existing,
                                                    ResourceType.DB,
                                                    DbResourceConfig.class)));
                    case FILE ->
                            JSONUtil.toJsonStr(
                                    buildFile(
                                            request.getFileConfig(),
                                            previous(
                                                    existing,
                                                    ResourceType.FILE,
                                                    FileResourceConfig.class)));
                    case SERVICE ->
                            JSONUtil.toJsonStr(
                                    buildService(
                                            request.getServiceConfig(),
                                            previous(
                                                    existing,
                                                    ResourceType.SERVICE,
                                                    ServiceResourceConfig.class)));
                    case RABBITMQ, KAFKA -> buildMq(request, existing);
                    case MONITOR_SERVER ->
                            JSONUtil.toJsonStr(
                                    buildMonitor(
                                            request.getMonitorConfig(),
                                            previous(
                                                    existing,
                                                    ResourceType.MONITOR_SERVER,
                                                    MonitorServerResourceConfig.class)));
                    case SSH ->
                            JSONUtil.toJsonStr(
                                    buildSsh(
                                            request.getSshConfig(),
                                            previous(
                                                    existing,
                                                    ResourceType.SSH,
                                                    SshResourceConfig.class)));
                };
        return cipher.encrypt(plaintext);
    }

    public ResourceConfigView view(Resource resource) {
        return switch (resource.getType()) {
            case DB -> dbView(read(resource, DbResourceConfig.class));
            case FILE -> fileView(read(resource, FileResourceConfig.class));
            case SERVICE -> serviceView(read(resource, ServiceResourceConfig.class));
            case RABBITMQ -> rabbitView(read(resource, RabbitResourceConfig.class));
            case KAFKA -> kafkaView(read(resource, KafkaResourceConfig.class));
            case MONITOR_SERVER -> monitorView(read(resource, MonitorServerResourceConfig.class));
            case SSH -> sshView(read(resource, SshResourceConfig.class));
        };
    }

    String decryptForRuntime(Resource resource) {
        if (resource == null || resource.getConfig() == null || resource.getConfig().isBlank()) {
            throw new BusinessException(
                    ResultCode.CONFLICT, CoreMessage.RESOURCE_CONFIGURATION_IS_UNAVAILABLE);
        }
        return cipher.decrypt(resource.getConfig());
    }

    private DbResourceConfig buildDb(ResourceConfigRequest.Db request, DbResourceConfig previous) {
        DbType dbType =
                enumValue(
                        request.getDbType(),
                        DbType.class,
                        CoreMessage.DATABASE_TYPE_IS_REQUIRED_AND_MUST_BE_SUPPORTED);
        boolean retain =
                Boolean.TRUE.equals(request.getPreserveExisting())
                        && previous != null
                        && previous.getDbType() == dbType;
        String jdbcUrl =
                required(
                        retain && request.getJdbcUrl() == null
                                ? previous.getJdbcUrl()
                                : request.getJdbcUrl(),
                        CoreMessage.JDBC_URL_IS_REQUIRED);
        if (jdbcUrl.length() > MAX_ENDPOINT_LENGTH
                || !jdbcUrl.startsWith(jdbcPrefix(dbType))
                || SENSITIVE_JDBC_PATTERN.matcher(jdbcUrl).find()) {
            throw paramError(
                    CoreMessage
                            .JDBC_URL_DOES_NOT_MATCH_THE_DATABASE_TYPE_OR_CONTAINS_SENSITIVE_PARAMETERS);
        }
        String username =
                required(
                        retain && request.getUsername() == null
                                ? previous.getUsername()
                                : request.getUsername(),
                        CoreMessage.DATABASE_USERNAME_IS_REQUIRED);
        requireMax(username, 128, CoreMessage.DATABASE_USERNAME_MUST_NOT_EXCEED_128_CHARACTERS);

        boolean sameType = previous != null && previous.getDbType() == dbType;
        String password =
                mergeSecret(
                        request.getPassword(),
                        request.getClearPassword(),
                        sameType ? previous.getPassword() : null);
        requireMax(
                password,
                MAX_HEADER_VALUE_LENGTH,
                CoreMessage.DATABASE_PASSWORD_EXCEEDS_THE_LENGTH_LIMIT);

        DbResourceConfig config = new DbResourceConfig();
        config.setDbType(dbType);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setSchema(
                trimToNull(
                        retain && request.getSchema() == null
                                ? previous.getSchema()
                                : request.getSchema()));
        requireMax(
                config.getSchema(),
                128,
                CoreMessage.DATABASE_SCHEMA_MUST_NOT_EXCEED_128_CHARACTERS);
        return config;
    }

    private FileResourceConfig buildFile(
            ResourceConfigRequest.File request, FileResourceConfig previous) {
        if (Boolean.TRUE.equals(request.getPreserveExisting())
                && previous != null
                && (request.getProtocol() == null
                        || previous.getProtocol().name().equals(request.getProtocol()))) {
            if (request.getProtocol() == null) request.setProtocol(previous.getProtocol().name());
            if (request.getBasePath() == null) request.setBasePath(previous.getBasePath());
            if (request.getTimeout() == null) request.setTimeout(previous.getTimeout());
            if (request.getHost() == null) request.setHost(previous.getHost());
            if (request.getPort() == null) request.setPort(previous.getPort());
            if (request.getUsername() == null) request.setUsername(previous.getUsername());
            if (request.getEndpoint() == null) request.setEndpoint(previous.getEndpoint());
            if (request.getRegion() == null) request.setRegion(previous.getRegion());
            if (request.getBucket() == null) request.setBucket(previous.getBucket());
        }
        FileProtocol protocol =
                enumValue(
                        request.getProtocol(),
                        FileProtocol.class,
                        CoreMessage.FILE_PROTOCOL_IS_REQUIRED_AND_MUST_BE_SUPPORTED);
        int timeout = defaultValue(request.getTimeout(), 30000);
        requireRange(
                timeout,
                100,
                300000,
                CoreMessage.FILE_RESOURCE_TIMEOUT_MUST_BE_BETWEEN_100_AND_300000_MILLISECONDS);

        FileResourceConfig config = new FileResourceConfig();
        config.setProtocol(protocol);
        config.setTimeout(timeout);
        config.setBasePath(
                required(request.getBasePath(), CoreMessage.FILE_RESOURCE_BASEPATH_IS_REQUIRED));
        requireMax(
                config.getBasePath(),
                MAX_ENDPOINT_LENGTH,
                CoreMessage.FILE_RESOURCE_BASEPATH_EXCEEDS_THE_LENGTH_LIMIT);

        if (protocol != FileProtocol.SFTP
                && (hasText(request.getHostKey()) || hasText(request.getHostKeyVerification())))
            throw paramError(CoreMessage.SSH_HOST_KEY_INVALID);
        if (protocol == FileProtocol.LOCAL) {
            rejectRemoteFileFields(request);
            return config;
        }

        boolean sameProtocol = previous != null && previous.getProtocol() == protocol;
        if (protocol == FileProtocol.S3_COMPATIBLE) {
            rejectS3UnsupportedFields(request);
            config.setEndpoint(
                    optionalS3Endpoint(request.getEndpoint(), CoreMessage.S3_ENDPOINT_IS_INVALID));
            config.setRegion(required(request.getRegion(), CoreMessage.S3_REGION_IS_REQUIRED));
            requireMax(config.getRegion(), 128, CoreMessage.S3_REGION_EXCEEDS_THE_LENGTH_LIMIT);
            config.setBucket(required(request.getBucket(), CoreMessage.S3_BUCKET_IS_REQUIRED));
            if (!BUCKET_PATTERN.matcher(config.getBucket()).matches()) {
                throw paramError(CoreMessage.S3_BUCKET_NAME_IS_INVALID);
            }
            config.setAccessKey(
                    request.getAccessKey() == null && sameProtocol
                            ? previous.getAccessKey()
                            : trimToNull(request.getAccessKey()));
            if (!hasText(config.getAccessKey())) {
                throw paramError(CoreMessage.S3_ACCESSKEY_IS_REQUIRED);
            }
            requireMax(
                    config.getAccessKey(), 512, CoreMessage.S3_ACCESSKEY_EXCEEDS_THE_LENGTH_LIMIT);
            config.setSecretKey(
                    mergeSecret(
                            request.getSecretKey(),
                            request.getClearSecretKey(),
                            sameProtocol ? previous.getSecretKey() : null));
            if (!hasText(config.getSecretKey())) {
                throw paramError(CoreMessage.S3_SECRETKEY_IS_REQUIRED);
            }
            requireMax(
                    config.getSecretKey(),
                    MAX_HEADER_VALUE_LENGTH,
                    CoreMessage.S3_SECRETKEY_EXCEEDS_THE_LENGTH_LIMIT);
            config.setSessionToken(
                    mergeSecret(
                            request.getSessionToken(),
                            request.getClearSessionToken(),
                            sameProtocol ? previous.getSessionToken() : null));
            requireMax(
                    config.getSessionToken(),
                    MAX_SECRET_LENGTH,
                    CoreMessage.S3_SESSIONTOKEN_EXCEEDS_THE_LENGTH_LIMIT);
            config.setPathStyleAccess(
                    request.getPathStyleAccess() == null
                            ? sameProtocol
                                    ? previous.isPathStyleAccess()
                                    : hasText(config.getEndpoint())
                            : request.getPathStyleAccess());
            return config;
        }
        rejectObjectStorageFields(request);

        config.setHost(required(request.getHost(), CoreMessage.REMOTE_FILE_HOST_IS_REQUIRED));
        requireMax(
                config.getHost(), 255, CoreMessage.REMOTE_FILE_HOST_MUST_NOT_EXCEED_255_CHARACTERS);
        config.setPort(defaultValue(request.getPort(), protocol == FileProtocol.FTP ? 21 : 22));
        requireRange(config.getPort(), 1, 65535, CoreMessage.REMOTE_FILE_PORT_IS_INVALID);
        config.setUsername(
                required(request.getUsername(), CoreMessage.REMOTE_FILE_USERNAME_IS_REQUIRED));
        requireMax(
                config.getUsername(),
                128,
                CoreMessage.REMOTE_FILE_USERNAME_MUST_NOT_EXCEED_128_CHARACTERS);

        config.setPassword(
                mergeSecret(
                        request.getPassword(),
                        request.getClearPassword(),
                        sameProtocol ? previous.getPassword() : null));
        requireMax(
                config.getPassword(),
                MAX_HEADER_VALUE_LENGTH,
                CoreMessage.REMOTE_FILE_PASSWORD_EXCEEDS_THE_LENGTH_LIMIT);

        if (protocol == FileProtocol.FTP) {
            if (!hasText(config.getPassword())) {
                throw paramError(CoreMessage.FTP_RESOURCE_PASSWORD_IS_REQUIRED);
            }
            if (hasText(request.getPrivateKey())
                    || hasText(request.getPassphrase())
                    || hasText(request.getKnownHosts())) {
                throw paramError(CoreMessage.FTP_RESOURCES_DO_NOT_SUPPORT_SFTP_SECURITY_SETTINGS);
            }
            return config;
        }

        config.setPrivateKey(
                mergeSecret(
                        request.getPrivateKey(),
                        request.getClearPrivateKey(),
                        sameProtocol ? previous.getPrivateKey() : null));
        config.setPassphrase(
                mergeSecret(
                        request.getPassphrase(),
                        request.getClearPassphrase(),
                        sameProtocol ? previous.getPassphrase() : null));
        config.setKnownHosts(
                request.getKnownHosts() == null && sameProtocol
                        ? previous.getKnownHosts()
                        : trimToNull(request.getKnownHosts()));
        requireMax(
                config.getPrivateKey(),
                MAX_SECRET_LENGTH,
                CoreMessage.SFTP_PRIVATE_KEY_EXCEEDS_THE_LENGTH_LIMIT);
        requireMax(
                config.getPassphrase(),
                MAX_HEADER_VALUE_LENGTH,
                CoreMessage.SFTP_PASSPHRASE_EXCEEDS_THE_LENGTH_LIMIT);
        requireMax(
                config.getKnownHosts(),
                MAX_ENDPOINT_LENGTH,
                CoreMessage.SFTP_KNOWNHOSTS_PATH_EXCEEDS_THE_LENGTH_LIMIT);
        if (!hasText(config.getPassword()) && !hasText(config.getPrivateKey())) {
            throw paramError(CoreMessage.SFTP_RESOURCES_REQUIRE_A_PASSWORD_OR_PRIVATE_KEY);
        }
        if (hasText(config.getPassphrase()) && !hasText(config.getPrivateKey())) {
            throw paramError(CoreMessage.SFTP_PASSPHRASE_REQUIRES_A_PRIVATE_KEY);
        }
        String mode = request.getHostKeyVerification();
        if (mode == null)
            mode =
                    sameProtocol
                            ? previous.getHostKeyVerification() == null
                                    ? "KNOWN_HOSTS"
                                    : previous.getHostKeyVerification()
                            : hasText(config.getKnownHosts()) ? "KNOWN_HOSTS" : "MANAGED";
        if (!Set.of("MANAGED", "KNOWN_HOSTS").contains(mode))
            throw paramError(CoreMessage.SSH_HOST_KEY_INVALID);
        config.setHostKeyVerification(mode);
        if ("KNOWN_HOSTS".equals(mode)) {
            if (!hasText(config.getKnownHosts()))
                throw paramError(CoreMessage.SFTP_RESOURCES_REQUIRE_A_KNOWNHOSTS_FILE);
        } else {
            if (!SshHostKeys.validHost(config.getHost()))
                throw paramError(CoreMessage.SSH_HOST_KEY_INVALID);
            String key = request.getHostKey();
            if (key == null
                    && sameProtocol
                    && "MANAGED".equals(previous.getHostKeyVerification())
                    && config.getHost().equals(previous.getHost())
                    && config.getPort().equals(previous.getPort())) key = previous.getHostKey();
            if (!hasText(key)) throw paramError(CoreMessage.SSH_HOST_KEY_REQUIRED);
            config.setHostKey(Base64.getEncoder().encodeToString(SshHostKeys.decode(key)));
            config.setKnownHosts(null);
        }
        return config;
    }

    private ServiceResourceConfig buildService(
            ResourceConfigRequest.Service request, ServiceResourceConfig previous) {
        request = retainService(request, previous);
        ServiceProtocol protocol =
                enumValue(
                        request.getProtocol(),
                        ServiceProtocol.class,
                        CoreMessage.SERVICE_PROTOCOL_IS_REQUIRED_AND_MUST_BE_SUPPORTED);
        String endpoint =
                validatedHttpUri(request.getEndpoint(), CoreMessage.SERVICE_ENDPOINT_IS_INVALID);
        int connectTimeout = defaultValue(request.getConnectTimeout(), 10);
        int readTimeout = defaultValue(request.getReadTimeout(), 30);
        int writeTimeout = defaultValue(request.getWriteTimeout(), 30);
        int callTimeout = defaultValue(request.getCallTimeout(), 60);
        requireRange(
                connectTimeout,
                1,
                300,
                CoreMessage.SERVICE_CONNECT_TIMEOUT_MUST_BE_BETWEEN_1_AND_300_SECONDS);
        requireRange(
                readTimeout,
                1,
                300,
                CoreMessage.SERVICE_READ_TIMEOUT_MUST_BE_BETWEEN_1_AND_300_SECONDS);
        requireRange(
                writeTimeout,
                1,
                300,
                CoreMessage.SERVICE_WRITE_TIMEOUT_MUST_BE_BETWEEN_1_AND_300_SECONDS);
        requireRange(
                callTimeout,
                1,
                900,
                CoreMessage.SERVICE_CALL_TIMEOUT_MUST_BE_BETWEEN_1_AND_900_SECONDS);

        ServiceResourceConfig config = new ServiceResourceConfig();
        config.setProtocol(protocol);
        ServiceAuthType authType =
                request.getAuthType() == null
                        ? ServiceAuthType.NONE
                        : enumValue(
                                request.getAuthType(),
                                ServiceAuthType.class,
                                CoreMessage.SERVICE_AUTHENTICATION_TYPE_IS_NOT_SUPPORTED);
        config.setAuthType(authType);
        config.setEndpoint(endpoint);
        config.setConnectTimeout(connectTimeout);
        config.setReadTimeout(readTimeout);
        config.setWriteTimeout(writeTimeout);
        config.setCallTimeout(callTimeout);
        config.setHeaders(
                request.getPreservedHeaderNames() != null
                        ? validatedHeaders(
                                HiddenHeaders.merge(
                                        request.getHeaders(),
                                        request.getPreservedHeaderNames(),
                                        previous != null && previous.getProtocol() == protocol
                                                ? previous.getHeaders()
                                                : null))
                        : request.getHeaders() == null
                                        && previous != null
                                        && previous.getProtocol() == protocol
                                ? copyHeaders(previous.getHeaders())
                                : validatedHeaders(request.getHeaders()));
        HiddenHeaders.merge(config.getHeaders(), null, null);

        if (protocol == ServiceProtocol.WEB_SERVICE) {
            if (authType != ServiceAuthType.NONE || request.getOauth2() != null) {
                throw paramError(
                        CoreMessage
                                .WEBSERVICE_DOES_NOT_SUPPORT_OAUTH2_CLIENT_CREDENTIALS_AUTHENTICATION);
            }
            config.setWebService(buildWebService(request.getWebService()));
        } else if (request.getWebService() != null) {
            throw paramError(CoreMessage.HTTP_SERVICES_MUST_NOT_INCLUDE_WEBSERVICE_SETTINGS);
        }
        if (authType == ServiceAuthType.OAUTH2_CLIENT_CREDENTIALS) {
            if (containsHeader(config.getHeaders(), "Authorization")) {
                throw paramError(
                        CoreMessage
                                .OAUTH2_SERVICE_RESOURCES_MUST_NOT_CONFIGURE_AN_AUTHORIZATION_HEADER);
            }
            OAuth2ClientCredentialsConfig previousOauth =
                    previous != null
                                    && previous.getProtocol() == protocol
                                    && previous.getAuthType()
                                            == ServiceAuthType.OAUTH2_CLIENT_CREDENTIALS
                            ? previous.getOauth2()
                            : null;
            config.setOauth2(buildOAuth2(request.getOauth2(), previousOauth));
        } else if (request.getOauth2() != null) {
            throw paramError(CoreMessage.NONE_AUTHENTICATION_MUST_NOT_INCLUDE_OAUTH2_SETTINGS);
        }
        return config;
    }

    private ResourceConfigRequest.Service retainService(
            ResourceConfigRequest.Service input, ServiceResourceConfig previous) {
        if (!Boolean.TRUE.equals(input.getPreserveExisting()) || previous == null) return input;
        ResourceConfigRequest.Service request =
                JSONUtil.toBean(JSONUtil.toJsonStr(input), ResourceConfigRequest.Service.class);
        if (request.getProtocol() == null) request.setProtocol(previous.getProtocol().name());
        if (!previous.getProtocol().name().equals(request.getProtocol())) return request;
        if (request.getEndpoint() == null) request.setEndpoint(previous.getEndpoint());
        if (request.getAuthType() == null)
            request.setAuthType(
                    previous.getAuthType() == null ? "NONE" : previous.getAuthType().name());
        if (request.getConnectTimeout() == null)
            request.setConnectTimeout(previous.getConnectTimeout());
        if (request.getReadTimeout() == null) request.setReadTimeout(previous.getReadTimeout());
        if (request.getWriteTimeout() == null) request.setWriteTimeout(previous.getWriteTimeout());
        if (request.getCallTimeout() == null) request.setCallTimeout(previous.getCallTimeout());
        if (previous.getProtocol() == ServiceProtocol.WEB_SERVICE
                && request.getWebService() == null)
            request.setWebService(
                    JSONUtil.toBean(
                            JSONUtil.toJsonStr(previous.getWebService()),
                            ResourceConfigRequest.WebService.class));
        if ("OAUTH2_CLIENT_CREDENTIALS".equals(request.getAuthType())
                && previous.getAuthType() == ServiceAuthType.OAUTH2_CLIENT_CREDENTIALS
                && previous.getOauth2() != null) {
            var old = previous.getOauth2();
            var oauth = request.getOauth2();
            if (oauth == null) oauth = new ResourceConfigRequest.OAuth2ClientCredentials();
            if (oauth.getTokenEndpoint() == null) oauth.setTokenEndpoint(old.getTokenEndpoint());
            if (oauth.getClientId() == null) oauth.setClientId(old.getClientId());
            if (oauth.getScopes() == null) oauth.setScopes(old.getScopes());
            if (oauth.getAudience() == null) oauth.setAudience(old.getAudience());
            if (oauth.getClientAuthenticationMethod() == null)
                oauth.setClientAuthenticationMethod(old.getClientAuthenticationMethod().name());
            if (oauth.getRefreshSkewSeconds() == null)
                oauth.setRefreshSkewSeconds(old.getRefreshSkewSeconds());
            request.setOauth2(oauth);
        }
        return request;
    }

    private OAuth2ClientCredentialsConfig buildOAuth2(
            ResourceConfigRequest.OAuth2ClientCredentials request,
            OAuth2ClientCredentialsConfig previous) {
        if (request == null) {
            throw paramError(CoreMessage.OAUTH2_CLIENT_CREDENTIALS_CONFIGURATION_IS_REQUIRED);
        }
        OAuth2ClientCredentialsConfig config = new OAuth2ClientCredentialsConfig();
        config.setTokenEndpoint(
                validatedHttpUri(
                        request.getTokenEndpoint(), CoreMessage.OAUTH2_TOKENENDPOINT_IS_INVALID));
        config.setClientId(
                required(request.getClientId(), CoreMessage.OAUTH2_CLIENTID_IS_REQUIRED));
        requireMax(config.getClientId(), 512, CoreMessage.OAUTH2_CLIENTID_EXCEEDS_THE_LENGTH_LIMIT);
        config.setClientSecret(
                mergeSecret(
                        request.getClientSecret(),
                        request.getClearClientSecret(),
                        previous == null ? null : previous.getClientSecret()));
        if (!hasText(config.getClientSecret())) {
            throw paramError(CoreMessage.OAUTH2_CLIENTSECRET_IS_REQUIRED);
        }
        requireMax(
                config.getClientSecret(),
                MAX_HEADER_VALUE_LENGTH,
                CoreMessage.OAUTH2_CLIENTSECRET_EXCEEDS_THE_LENGTH_LIMIT);
        config.setScopes(
                Boolean.TRUE.equals(request.getClearScopes())
                        ? null
                        : trimToNull(request.getScopes()));
        config.setAudience(
                Boolean.TRUE.equals(request.getClearAudience())
                        ? null
                        : trimToNull(request.getAudience()));
        requireMax(config.getScopes(), 2048, CoreMessage.OAUTH2_SCOPES_EXCEEDS_THE_LENGTH_LIMIT);
        requireMax(
                config.getAudience(), 2048, CoreMessage.OAUTH2_AUDIENCE_EXCEEDS_THE_LENGTH_LIMIT);
        config.setClientAuthenticationMethod(
                request.getClientAuthenticationMethod() == null
                        ? OAuth2ClientAuthenticationMethod.CLIENT_SECRET_BASIC
                        : enumValue(
                                request.getClientAuthenticationMethod(),
                                OAuth2ClientAuthenticationMethod.class,
                                CoreMessage.OAUTH2_CLIENT_AUTHENTICATION_METHOD_IS_NOT_SUPPORTED));
        int refreshSkew = defaultValue(request.getRefreshSkewSeconds(), 30);
        requireRange(
                refreshSkew,
                0,
                300,
                CoreMessage.OAUTH2_TOKEN_REFRESH_SKEW_MUST_BE_BETWEEN_0_AND_300_SECONDS);
        config.setRefreshSkewSeconds(refreshSkew);
        return config;
    }

    private WebServiceConfig buildWebService(ResourceConfigRequest.WebService request) {
        if (request == null) {
            throw paramError(CoreMessage.WEBSERVICE_CONFIGURATION_IS_REQUIRED);
        }
        WebServiceConfig config = new WebServiceConfig();
        config.setWsdl(validatedHttpUri(request.getWsdl(), CoreMessage.WSDL_URL_IS_INVALID));
        config.setNamespace(required(request.getNamespace(), CoreMessage.NAMESPACE_IS_REQUIRED));
        config.setServiceName(
                required(request.getServiceName(), CoreMessage.SERVICENAME_IS_REQUIRED));
        config.setPortName(required(request.getPortName(), CoreMessage.PORTNAME_IS_REQUIRED));
        requireMax(config.getNamespace(), 512, CoreMessage.NAMESPACE_EXCEEDS_THE_LENGTH_LIMIT);
        requireMax(config.getServiceName(), 128, CoreMessage.SERVICENAME_EXCEEDS_THE_LENGTH_LIMIT);
        requireMax(config.getPortName(), 128, CoreMessage.PORTNAME_EXCEEDS_THE_LENGTH_LIMIT);
        return config;
    }

    /** Merge on the encrypted server-side document; unknown fields never leave the API. */
    private String buildMq(ResourceUpsertRequest request, Resource existing) {
        boolean rabbit = request.getType() == ResourceType.RABBITMQ;
        boolean preserve =
                Boolean.TRUE.equals(
                        rabbit
                                ? request.getRabbitConfig().getPreserveExisting()
                                : request.getKafkaConfig().getPreserveExisting());
        try {
            var mapper =
                    new ObjectMapper()
                            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
            if (existing != null && existing.getType() != request.getType())
                throw paramError(
                        CoreMessage
                                .EXACTLY_ONE_CONFIGURATION_MATCHING_THE_RESOURCE_TYPE_IS_REQUIRED);
            if (preserve && existing == null)
                throw paramError(CoreMessage.RESOURCE_CONFIGURATION_IS_UNAVAILABLE);
            var original =
                    existing == null
                            ? mapper.createObjectNode()
                            : mapper.readTree(decryptForRuntime(existing));
            if (!original.isObject())
                throw paramError(CoreMessage.RESOURCE_CONFIGURATION_IS_UNAVAILABLE);
            var result = (ObjectNode) original.deepCopy();
            var merged = mapper.createObjectNode();
            List<String> fields =
                    rabbit
                            ? List.of(
                                    "host",
                                    "port",
                                    "username",
                                    "password",
                                    "virtualHost",
                                    "exchange",
                                    "routingKey",
                                    "publisherConfirm")
                            : List.of("bootstrapServers", "acks");
            var input =
                    mapper.valueToTree(
                            rabbit ? request.getRabbitConfig() : request.getKafkaConfig());
            for (String field : fields) {
                var value = input.get(field);
                if ((value == null || value.isNull()) && preserve) value = original.get(field);
                if (value != null && !value.isNull()) {
                    boolean valid =
                            field.equals("port")
                                    ? value.isIntegralNumber() && value.canConvertToInt()
                                    : field.equals("publisherConfirm")
                                            ? value.isBoolean()
                                            : value.isTextual();
                    if (!valid) throw paramError(CoreMessage.RESOURCE_CONFIGURATION_IS_UNAVAILABLE);
                    merged.set(field, value);
                }
            }
            JsonNode normalized;
            if (rabbit) {
                var dto = mapper.treeToValue(merged, ResourceConfigRequest.Rabbit.class);
                dto.setClearPassword(request.getRabbitConfig().getClearPassword());
                normalized =
                        mapper.valueToTree(
                                buildRabbit(
                                        dto,
                                        previous(
                                                existing,
                                                ResourceType.RABBITMQ,
                                                RabbitResourceConfig.class)));
            } else {
                normalized =
                        mapper.valueToTree(
                                buildKafka(
                                        mapper.treeToValue(
                                                merged, ResourceConfigRequest.Kafka.class)));
            }
            for (String field : fields) result.set(field, normalized.get(field));
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw paramError(CoreMessage.RESOURCE_CONFIGURATION_IS_UNAVAILABLE);
        }
    }

    private RabbitResourceConfig buildRabbit(
            ResourceConfigRequest.Rabbit request, RabbitResourceConfig previous) {
        RabbitResourceConfig config = new RabbitResourceConfig();
        config.setHost(required(request.getHost(), CoreMessage.RABBITMQ_HOST_IS_REQUIRED));
        requireMax(config.getHost(), 255, CoreMessage.RABBITMQ_HOST_MUST_NOT_EXCEED_255_CHARACTERS);
        config.setPort(defaultValue(request.getPort(), 5672));
        requireRange(config.getPort(), 1, 65535, CoreMessage.RABBITMQ_PORT_IS_INVALID);
        config.setUsername(
                required(request.getUsername(), CoreMessage.RABBITMQ_USERNAME_IS_REQUIRED));
        requireMax(
                config.getUsername(),
                128,
                CoreMessage.RABBITMQ_USERNAME_MUST_NOT_EXCEED_128_CHARACTERS);
        config.setPassword(
                mergeSecret(
                        request.getPassword(),
                        request.getClearPassword(),
                        previous == null ? null : previous.getPassword()));
        if (!hasText(config.getPassword())) {
            throw paramError(CoreMessage.RABBITMQ_PASSWORD_IS_REQUIRED);
        }
        requireMax(
                config.getPassword(),
                MAX_HEADER_VALUE_LENGTH,
                CoreMessage.RABBITMQ_PASSWORD_EXCEEDS_THE_LENGTH_LIMIT);
        config.setVirtualHost(defaultText(request.getVirtualHost(), "/"));
        config.setExchange(trimToNull(request.getExchange()));
        config.setRoutingKey(trimToNull(request.getRoutingKey()));
        config.setPublisherConfirm(defaultValue(request.getPublisherConfirm(), true));
        requireMax(
                config.getVirtualHost(), 255, CoreMessage.RABBITMQ_VHOST_EXCEEDS_THE_LENGTH_LIMIT);
        requireMax(
                config.getExchange(), 255, CoreMessage.RABBITMQ_EXCHANGE_EXCEEDS_THE_LENGTH_LIMIT);
        requireMax(
                config.getRoutingKey(),
                255,
                CoreMessage.RABBITMQ_ROUTINGKEY_EXCEEDS_THE_LENGTH_LIMIT);
        return config;
    }

    private KafkaResourceConfig buildKafka(ResourceConfigRequest.Kafka request) {
        KafkaResourceConfig config = new KafkaResourceConfig();
        config.setBootstrapServers(
                required(
                        request.getBootstrapServers(),
                        CoreMessage.KAFKA_BOOTSTRAPSERVERS_IS_REQUIRED));
        requireMax(
                config.getBootstrapServers(),
                MAX_ENDPOINT_LENGTH,
                CoreMessage.KAFKA_BOOTSTRAPSERVERS_EXCEEDS_THE_LENGTH_LIMIT);
        String acks = defaultText(request.getAcks(), "all");
        if (!Set.of("all", "1", "0").contains(acks)) {
            throw paramError(CoreMessage.KAFKA_ACKS_MUST_BE_ALL_1_OR_0);
        }
        config.setAcks(acks);
        return config;
    }

    private MonitorServerResourceConfig buildMonitor(
            ResourceConfigRequest.Monitor request, MonitorServerResourceConfig previous) {
        if (Boolean.TRUE.equals(request.getPreserveExisting()) && previous != null) {
            request =
                    JSONUtil.toBean(
                            JSONUtil.toJsonStr(request), ResourceConfigRequest.Monitor.class);
            if (request.getProtocol() == null) request.setProtocol(previous.getProtocol().name());
            if (request.getEndpoint() == null) request.setEndpoint(previous.getEndpoint());
            if (request.getConnectTimeout() == null)
                request.setConnectTimeout(previous.getConnectTimeout());
            if (request.getReadTimeout() == null) request.setReadTimeout(previous.getReadTimeout());
            if (request.getOsType() == null && previous.getOsType() != null)
                request.setOsType(previous.getOsType().name());
            if (request.getAgentVersion() == null)
                request.setAgentVersion(previous.getAgentVersion());
        }
        MonitorProtocol protocol =
                enumValue(
                        request.getProtocol(),
                        MonitorProtocol.class,
                        CoreMessage.MONITOR_PROTOCOL_IS_REQUIRED_AND_MUST_BE_SUPPORTED);
        String endpoint =
                validatedHttpUri(request.getEndpoint(), CoreMessage.MONITOR_ENDPOINT_IS_INVALID);
        if (URI.create(endpoint).getRawQuery() != null
                || URI.create(endpoint).getRawFragment() != null) {
            throw paramError(CoreMessage.MONITOR_ENDPOINT_IS_INVALID);
        }
        if (!URI.create(endpoint).getScheme().equalsIgnoreCase(protocol.name())) {
            throw paramError(CoreMessage.MONITOR_ENDPOINT_DOES_NOT_MATCH_THE_PROTOCOL);
        }
        int connectTimeout = defaultValue(request.getConnectTimeout(), 5000);
        int readTimeout = defaultValue(request.getReadTimeout(), 10000);
        requireRange(
                connectTimeout,
                100,
                300000,
                CoreMessage.MONITOR_CONNECT_TIMEOUT_MUST_BE_BETWEEN_100_AND_300000_MILLISECONDS);
        requireRange(
                readTimeout,
                100,
                300000,
                CoreMessage.MONITOR_READ_TIMEOUT_MUST_BE_BETWEEN_100_AND_300000_MILLISECONDS);

        MonitorServerResourceConfig config = new MonitorServerResourceConfig();
        config.setProtocol(protocol);
        config.setEndpoint(endpoint);
        config.setConnectTimeout(connectTimeout);
        config.setReadTimeout(readTimeout);
        config.setToken(
                mergeSecret(
                        request.getToken(),
                        request.getClearToken(),
                        previous == null ? null : previous.getToken()));
        requireMax(
                config.getToken(),
                MAX_HEADER_VALUE_LENGTH,
                CoreMessage.MONITOR_TOKEN_EXCEEDS_THE_LENGTH_LIMIT);
        config.setHeaders(
                request.getPreservedHeaderNames() != null
                        ? validatedHeaders(
                                HiddenHeaders.merge(
                                        request.getHeaders(),
                                        request.getPreservedHeaderNames(),
                                        previous == null ? null : previous.getHeaders()))
                        : request.getHeaders() == null && previous != null
                                ? copyHeaders(previous.getHeaders())
                                : validatedHeaders(request.getHeaders()));
        HiddenHeaders.merge(config.getHeaders(), null, null);
        config.setOsType(
                request.getOsType() == null
                        ? null
                        : enumValue(
                                request.getOsType(),
                                OSType.class,
                                CoreMessage.MONITOR_OPERATING_SYSTEM_TYPE_IS_NOT_SUPPORTED));
        config.setAgentVersion(trimToNull(request.getAgentVersion()));
        requireMax(
                config.getAgentVersion(),
                64,
                CoreMessage.MONITOR_AGENT_VERSION_MUST_NOT_EXCEED_64_CHARACTERS);
        return config;
    }

    private SshResourceConfig buildSsh(
            ResourceConfigRequest.Ssh request, SshResourceConfig previous) {
        boolean retain = Boolean.TRUE.equals(request.getPreserveExisting()) && previous != null;
        SshResourceConfig config = new SshResourceConfig();
        config.setHost(
                required(
                        retain && request.getHost() == null
                                ? previous.getHost()
                                : request.getHost(),
                        CoreMessage.SSH_HOST_IS_REQUIRED));
        requireMax(config.getHost(), 255, CoreMessage.SSH_HOST_MUST_NOT_EXCEED_255_CHARACTERS);
        config.setPort(defaultValue(request.getPort(), retain ? previous.getPort() : 22));
        requireRange(config.getPort(), 1, 65535, CoreMessage.SSH_PORT_IS_INVALID);
        config.setUsername(
                required(
                        retain && request.getUsername() == null
                                ? previous.getUsername()
                                : request.getUsername(),
                        CoreMessage.SSH_USERNAME_IS_REQUIRED));
        requireMax(
                config.getUsername(), 128, CoreMessage.SSH_USERNAME_MUST_NOT_EXCEED_128_CHARACTERS);
        config.setConnectTimeout(
                defaultValue(
                        request.getConnectTimeout(),
                        retain ? previous.getConnectTimeout() : 30000));
        requireRange(
                config.getConnectTimeout(),
                100,
                300000,
                CoreMessage.SSH_CONNECT_TIMEOUT_MUST_BE_BETWEEN_100_AND_300000_MILLISECONDS);
        config.setPassword(
                mergeSecret(
                        request.getPassword(),
                        request.getClearPassword(),
                        previous == null ? null : previous.getPassword()));
        config.setPrivateKey(
                mergeSecret(
                        request.getPrivateKey(),
                        request.getClearPrivateKey(),
                        previous == null ? null : previous.getPrivateKey()));
        config.setPassphrase(
                mergeSecret(
                        request.getPassphrase(),
                        request.getClearPassphrase(),
                        previous == null ? null : previous.getPassphrase()));
        config.setKnownHosts(
                request.getKnownHosts() == null && previous != null
                        ? previous.getKnownHosts()
                        : trimToNull(request.getKnownHosts()));
        requireMax(
                config.getPassword(),
                MAX_HEADER_VALUE_LENGTH,
                CoreMessage.SSH_PASSWORD_EXCEEDS_THE_LENGTH_LIMIT);
        requireMax(
                config.getPrivateKey(),
                MAX_SECRET_LENGTH,
                CoreMessage.SSH_PRIVATE_KEY_EXCEEDS_THE_LENGTH_LIMIT);
        requireMax(
                config.getPassphrase(),
                MAX_HEADER_VALUE_LENGTH,
                CoreMessage.SSH_PASSPHRASE_EXCEEDS_THE_LENGTH_LIMIT);
        requireMax(
                config.getKnownHosts(),
                MAX_ENDPOINT_LENGTH,
                CoreMessage.SSH_KNOWNHOSTS_PATH_EXCEEDS_THE_LENGTH_LIMIT);
        if (!hasText(config.getPassword()) && !hasText(config.getPrivateKey())) {
            throw paramError(CoreMessage.SSH_RESOURCES_REQUIRE_A_PASSWORD_OR_PRIVATE_KEY);
        }
        if (hasText(config.getPassphrase()) && !hasText(config.getPrivateKey())) {
            throw paramError(CoreMessage.SSH_PASSPHRASE_REQUIRES_A_PRIVATE_KEY);
        }
        String mode =
                request.getHostKeyVerification() == null
                        ? previous != null
                                ? SshHostKeys.mode(previous)
                                : hasText(config.getKnownHosts()) ? "KNOWN_HOSTS" : "MANAGED"
                        : request.getHostKeyVerification();
        if (!Set.of("MANAGED", "KNOWN_HOSTS").contains(mode))
            throw paramError(CoreMessage.SSH_HOST_KEY_INVALID);
        config.setHostKeyVerification(mode);
        if ("KNOWN_HOSTS".equals(mode)) {
            if (!hasText(config.getKnownHosts()))
                throw paramError(CoreMessage.SSH_RESOURCES_REQUIRE_A_KNOWNHOSTS_FILE);
        } else {
            if (!SshHostKeys.validHost(config.getHost()))
                throw paramError(CoreMessage.SSH_HOST_KEY_INVALID);
            String hostKey = request.getHostKey();
            if (hostKey == null
                    && previous != null
                    && "MANAGED".equals(SshHostKeys.mode(previous))
                    && config.getHost().equals(previous.getHost())
                    && config.getPort() == previous.getPort()) hostKey = previous.getHostKey();
            if (!hasText(hostKey)) throw paramError(CoreMessage.SSH_HOST_KEY_REQUIRED);
            config.setHostKey(Base64.getEncoder().encodeToString(SshHostKeys.decode(hostKey)));
            config.setKnownHosts(null);
        }
        return config;
    }

    private ResourceConfigView.Db dbView(DbResourceConfig config) {
        ResourceConfigView.Db view = new ResourceConfigView.Db();
        view.setDbType(config.getDbType().name());
        view.setJdbcUrl(config.getJdbcUrl());
        view.setUsername(config.getUsername());
        view.setJdbcUrlConfigured(hasText(config.getJdbcUrl()));
        view.setUsernameConfigured(hasText(config.getUsername()));
        view.setPasswordConfigured(hasText(config.getPassword()));
        view.setSchema(config.getSchema());
        return view;
    }

    private ResourceConfigView.File fileView(FileResourceConfig config) {
        ResourceConfigView.File view = new ResourceConfigView.File();
        view.setHost(config.getHost());
        view.setUsername(config.getUsername());
        view.setBasePath(config.getBasePath());
        view.setEndpoint(config.getEndpoint());
        view.setBucket(config.getBucket());
        view.setKnownHosts(config.getKnownHosts());
        if (config.getProtocol() == FileProtocol.SFTP) {
            String mode =
                    config.getHostKeyVerification() == null
                            ? "KNOWN_HOSTS"
                            : config.getHostKeyVerification();
            view.setHostKeyVerification(mode);
            if ("MANAGED".equals(mode)) {
                view.setHostKeyFingerprint(SshHostKeys.fingerprint(config.getHostKey()));
                view.setHostKeyType(SshHostKeys.type(config.getHostKey()));
            }
        }
        view.setProtocol(config.getProtocol().name());
        view.setPort(config.getPort());
        view.setTimeout(config.getTimeout());
        view.setHostConfigured(hasText(config.getHost()));
        view.setUsernameConfigured(hasText(config.getUsername()));
        view.setPasswordConfigured(hasText(config.getPassword()));
        view.setPrivateKeyConfigured(hasText(config.getPrivateKey()));
        view.setPassphraseConfigured(hasText(config.getPassphrase()));
        view.setKnownHostsConfigured(hasText(config.getKnownHosts()));
        view.setBasePathConfigured(hasText(config.getBasePath()));
        view.setEndpointConfigured(hasText(config.getEndpoint()));
        view.setRegion(config.getRegion());
        view.setBucketConfigured(hasText(config.getBucket()));
        view.setAccessKeyConfigured(hasText(config.getAccessKey()));
        view.setSecretKeyConfigured(hasText(config.getSecretKey()));
        view.setSessionTokenConfigured(hasText(config.getSessionToken()));
        view.setPathStyleAccess(config.isPathStyleAccess());
        return view;
    }

    private ResourceConfigView.Service serviceView(ServiceResourceConfig config) {
        ResourceConfigView.Service view = new ResourceConfigView.Service();
        view.setEndpoint(config.getEndpoint());
        if (config.getWebService() != null) {
            view.setWsdl(config.getWebService().getWsdl());
            view.setNamespace(config.getWebService().getNamespace());
            view.setServiceName(config.getWebService().getServiceName());
            view.setPortName(config.getWebService().getPortName());
        }
        view.setProtocol(config.getProtocol().name());
        ServiceAuthType authType =
                config.getAuthType() == null ? ServiceAuthType.NONE : config.getAuthType();
        view.setAuthType(authType.name());
        view.setConnectTimeout(config.getConnectTimeout());
        view.setReadTimeout(config.getReadTimeout());
        view.setWriteTimeout(config.getWriteTimeout());
        view.setCallTimeout(config.getCallTimeout());
        view.setEndpointConfigured(hasText(config.getEndpoint()));
        view.setHeaderNames(headerNames(config.getHeaders()));
        view.setWebServiceConfigured(config.getWebService() != null);
        OAuth2ClientCredentialsConfig oauth2 = config.getOauth2();
        view.setOauth2Configured(oauth2 != null);
        if (oauth2 != null) {
            view.setOauth2TokenEndpoint(oauth2.getTokenEndpoint());
            view.setOauth2ClientId(oauth2.getClientId());
            view.setOauth2Scopes(oauth2.getScopes());
            view.setOauth2Audience(oauth2.getAudience());
        }
        view.setOauth2ClientIdConfigured(oauth2 != null && hasText(oauth2.getClientId()));
        view.setOauth2ClientSecretConfigured(oauth2 != null && hasText(oauth2.getClientSecret()));
        view.setOauth2TokenEndpointConfigured(oauth2 != null && hasText(oauth2.getTokenEndpoint()));
        view.setOauth2ScopesConfigured(oauth2 != null && hasText(oauth2.getScopes()));
        view.setOauth2AudienceConfigured(oauth2 != null && hasText(oauth2.getAudience()));
        view.setOauth2ClientAuthenticationMethod(
                oauth2 == null ? null : oauth2.getClientAuthenticationMethod().name());
        view.setOauth2RefreshSkewSeconds(oauth2 == null ? null : oauth2.getRefreshSkewSeconds());
        return view;
    }

    private ResourceConfigView.Rabbit rabbitView(RabbitResourceConfig config) {
        ResourceConfigView.Rabbit view = new ResourceConfigView.Rabbit();
        view.setPort(config.getPort());
        view.setPublisherConfirm(config.isPublisherConfirm());
        view.setHost(config.getHost());
        view.setUsername(config.getUsername());
        view.setVirtualHost(config.getVirtualHost());
        view.setExchange(config.getExchange());
        view.setRoutingKey(config.getRoutingKey());
        view.setHostConfigured(hasText(config.getHost()));
        view.setUsernameConfigured(hasText(config.getUsername()));
        view.setPasswordConfigured(hasText(config.getPassword()));
        view.setVirtualHostConfigured(hasText(config.getVirtualHost()));
        view.setExchangeConfigured(hasText(config.getExchange()));
        view.setRoutingKeyConfigured(hasText(config.getRoutingKey()));
        return view;
    }

    private ResourceConfigView.Kafka kafkaView(KafkaResourceConfig config) {
        ResourceConfigView.Kafka view = new ResourceConfigView.Kafka();
        view.setAcks(config.getAcks());
        view.setBootstrapServers(config.getBootstrapServers());
        view.setBootstrapServersConfigured(hasText(config.getBootstrapServers()));
        return view;
    }

    private ResourceConfigView.Monitor monitorView(MonitorServerResourceConfig config) {
        ResourceConfigView.Monitor view = new ResourceConfigView.Monitor();
        view.setEndpoint(config.getEndpoint());
        view.setProtocol(config.getProtocol().name());
        view.setConnectTimeout(config.getConnectTimeout());
        view.setReadTimeout(config.getReadTimeout());
        view.setOsType(config.getOsType() == null ? null : config.getOsType().name());
        view.setAgentVersion(config.getAgentVersion());
        view.setEndpointConfigured(hasText(config.getEndpoint()));
        view.setTokenConfigured(hasText(config.getToken()));
        view.setHeaderNames(headerNames(config.getHeaders()));
        return view;
    }

    private ResourceConfigView.Ssh sshView(SshResourceConfig config) {
        ResourceConfigView.Ssh view = new ResourceConfigView.Ssh();
        view.setHostKeyVerification(SshHostKeys.mode(config));
        if ("MANAGED".equals(SshHostKeys.mode(config)) && hasText(config.getHostKey())) {
            view.setHostKeyFingerprint(SshHostKeys.fingerprint(config.getHostKey()));
            view.setHostKeyType(SshHostKeys.type(config.getHostKey()));
        }
        view.setHost(config.getHost());
        view.setUsername(config.getUsername());
        view.setKnownHosts(config.getKnownHosts());
        view.setPort(config.getPort());
        view.setConnectTimeout(config.getConnectTimeout());
        view.setHostConfigured(hasText(config.getHost()));
        view.setUsernameConfigured(hasText(config.getUsername()));
        view.setPasswordConfigured(hasText(config.getPassword()));
        view.setPrivateKeyConfigured(hasText(config.getPrivateKey()));
        view.setPassphraseConfigured(hasText(config.getPassphrase()));
        view.setKnownHostsConfigured(hasText(config.getKnownHosts()));
        return view;
    }

    private void requireMatchingConfig(ResourceUpsertRequest request) {
        if (request.getType() == null) {
            throw paramError(CoreMessage.RESOURCE_TYPE_IS_REQUIRED);
        }
        int count = 0;
        count += request.getDbConfig() == null ? 0 : 1;
        count += request.getFileConfig() == null ? 0 : 1;
        count += request.getServiceConfig() == null ? 0 : 1;
        count += request.getRabbitConfig() == null ? 0 : 1;
        count += request.getKafkaConfig() == null ? 0 : 1;
        count += request.getMonitorConfig() == null ? 0 : 1;
        count += request.getSshConfig() == null ? 0 : 1;
        boolean matches =
                switch (request.getType()) {
                    case DB -> request.getDbConfig() != null;
                    case FILE -> request.getFileConfig() != null;
                    case SERVICE -> request.getServiceConfig() != null;
                    case RABBITMQ -> request.getRabbitConfig() != null;
                    case KAFKA -> request.getKafkaConfig() != null;
                    case MONITOR_SERVER -> request.getMonitorConfig() != null;
                    case SSH -> request.getSshConfig() != null;
                };
        if (count != 1 || !matches) {
            throw paramError(
                    CoreMessage.EXACTLY_ONE_CONFIGURATION_MATCHING_THE_RESOURCE_TYPE_IS_REQUIRED);
        }
    }

    private <T> T previous(Resource existing, ResourceType expectedType, Class<T> type) {
        if (existing == null
                || existing.getType() == null
                || existing.getType() != expectedType
                || existing.getConfig() == null) {
            return null;
        }
        return read(existing, type);
    }

    private <T> T read(Resource resource, Class<T> type) {
        String plaintext = cipher.decrypt(resource.getConfig());
        return JSONUtil.toBean(plaintext, type);
    }

    private void rejectRemoteFileFields(ResourceConfigRequest.File request) {
        if (hasText(request.getHost())
                || request.getPort() != null
                || hasText(request.getUsername())
                || hasText(request.getPassword())
                || hasText(request.getPrivateKey())
                || hasText(request.getPassphrase())
                || hasText(request.getKnownHosts())
                || hasText(request.getEndpoint())
                || hasText(request.getRegion())
                || hasText(request.getBucket())
                || hasText(request.getAccessKey())
                || hasText(request.getSecretKey())
                || hasText(request.getSessionToken())) {
            throw paramError(
                    CoreMessage
                            .LOCAL_FILE_RESOURCES_MUST_NOT_INCLUDE_REMOTE_CONNECTION_OR_CREDENTIAL_FIELDS);
        }
    }

    private void rejectS3UnsupportedFields(ResourceConfigRequest.File request) {
        if (hasText(request.getHost())
                || request.getPort() != null
                || hasText(request.getUsername())
                || hasText(request.getPassword())
                || hasText(request.getPrivateKey())
                || hasText(request.getPassphrase())
                || hasText(request.getKnownHosts())) {
            throw paramError(
                    CoreMessage.S3_FILE_RESOURCES_MUST_NOT_INCLUDE_FTP_OR_SFTP_CONNECTION_FIELDS);
        }
    }

    private void rejectObjectStorageFields(ResourceConfigRequest.File request) {
        if (hasText(request.getEndpoint())
                || hasText(request.getRegion())
                || hasText(request.getBucket())
                || hasText(request.getAccessKey())
                || hasText(request.getSecretKey())
                || hasText(request.getSessionToken())
                || request.getPathStyleAccess() != null) {
            throw paramError(
                    CoreMessage.FTP_AND_SFTP_FILE_RESOURCES_MUST_NOT_INCLUDE_OBJECT_STORAGE_FIELDS);
        }
    }

    private Map<String, String> validatedHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return new LinkedHashMap<>();
        }
        if (headers.size() > MAX_HEADER_COUNT) {
            throw paramError(CoreMessage.HEADER_COUNT_MUST_NOT_EXCEED_50);
        }
        Map<String, String> result = new LinkedHashMap<>();
        headers.forEach(
                (name, value) -> {
                    String normalizedName = trimToNull(name);
                    if (normalizedName == null
                            || !HEADER_NAME_PATTERN.matcher(normalizedName).matches()) {
                        throw paramError(CoreMessage.HEADER_NAME_IS_INVALID);
                    }
                    if (value == null
                            || value.length() > MAX_HEADER_VALUE_LENGTH
                            || value.contains("\r")
                            || value.contains("\n")) {
                        throw paramError(
                                CoreMessage.HEADER_VALUE_IS_INVALID_OR_EXCEEDS_THE_LENGTH_LIMIT);
                    }
                    result.put(normalizedName, value);
                });
        return result;
    }

    private Map<String, String> copyHeaders(Map<String, String> headers) {
        return headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
    }

    private boolean containsHeader(Map<String, String> headers, String expected) {
        return headers != null
                && headers.keySet().stream().anyMatch(name -> expected.equalsIgnoreCase(name));
    }

    private Set<String> headerNames(Map<String, String> headers) {
        return headers == null ? Set.of() : new TreeSet<>(headers.keySet());
    }

    private String validatedHttpUri(String value, MessageDefinition message) {
        String normalized = required(value, message);
        if (normalized.length() > MAX_ENDPOINT_LENGTH) {
            throw paramError(message);
        }
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            if (scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || (uri.getRawQuery() != null
                            && SENSITIVE_QUERY_PATTERN.matcher(uri.getRawQuery()).find())) {
                throw paramError(message);
            }
            return uri.toString();
        } catch (URISyntaxException e) {
            throw paramError(message);
        }
    }

    private String optionalHttpUri(String value, MessageDefinition message) {
        return hasText(value) ? validatedHttpUri(value, message) : null;
    }

    private String optionalS3Endpoint(String value, MessageDefinition message) {
        String endpoint = optionalHttpUri(value, message);
        if (endpoint == null) {
            return null;
        }
        URI uri = URI.create(endpoint);
        if (uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || uri.getRawPath() != null
                        && !uri.getRawPath().isBlank()
                        && !"/".equals(uri.getRawPath())) {
            throw paramError(message);
        }
        return endpoint;
    }

    private String jdbcPrefix(DbType dbType) {
        return switch (dbType) {
            case MYSQL -> "jdbc:mysql:";
            case ORACLE -> "jdbc:oracle:";
            case POSTGRESQL -> "jdbc:postgresql:";
            case SQLSERVER -> "jdbc:sqlserver:";
        };
    }

    private String mergeSecret(String requested, Boolean clear, String previous) {
        if (Boolean.TRUE.equals(clear)) {
            return null;
        }
        return requested == null ? previous : requested;
    }

    private <E extends Enum<E>> E enumValue(
            String value, Class<E> type, MessageDefinition message) {
        if (!hasText(value)) {
            throw paramError(message);
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw paramError(message);
        }
    }

    private void requireRange(int value, int minimum, int maximum, MessageDefinition message) {
        if (value < minimum || value > maximum) {
            throw paramError(message);
        }
    }

    private void requireMax(String value, int maximum, MessageDefinition message) {
        if (value != null && value.length() > maximum) {
            throw paramError(message);
        }
    }

    private String required(String value, MessageDefinition message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw paramError(message);
        }
        return normalized;
    }

    private String defaultText(String value, String defaultValue) {
        String normalized = trimToNull(value);
        return normalized == null ? defaultValue : normalized;
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private <T> T defaultValue(T value, T defaultValue) {
        return value == null ? defaultValue : value;
    }

    private BusinessException paramError(MessageDefinition message, Object... arguments) {
        return new BusinessException(ResultCode.PARAM_ERROR, message, arguments);
    }
}
