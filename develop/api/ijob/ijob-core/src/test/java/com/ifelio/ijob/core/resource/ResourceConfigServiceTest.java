package com.ifelio.ijob.core.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.json.JSONUtil;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.contract.enums.ResourceType;
import com.ifelio.ijob.contract.resource.ResourceConfigRequest;
import com.ifelio.ijob.contract.resource.ResourceConfigView;
import com.ifelio.ijob.contract.resource.ResourceUpsertRequest;
import com.ifelio.ijob.core.resource.factory.db.DbResourceConfig;
import com.ifelio.ijob.core.resource.factory.file.FileResourceConfig;
import com.ifelio.ijob.core.resource.factory.service.ServiceResourceConfig;
import com.ifelio.ijob.core.resource.factory.ssh.SshResourceConfig;
import com.ifelio.ijob.core.resource.security.ResourceConfigCipher;
import com.ifelio.ijob.infra.entity.resource.Resource;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

class ResourceConfigServiceTest {
    @Test
    void monitorEditPreservesHiddenValuesAndProjectsOnlyAddressAndHeaderNames() {
        var request = monitorRequest();
        request.getMonitorConfig().setHeaders(Map.of("X-Keep", "hidden", "X-Remove", "old"));
        request.getMonitorConfig().setReadTimeout(24000);
        request.getMonitorConfig().setOsType("LINUX");
        request.getMonitorConfig().setAgentVersion("1.0");
        var existing = resource(ResourceType.MONITOR_SERVER, service.protect(request, null));
        var edit = new ResourceConfigRequest.Monitor();
        edit.setPreserveExisting(true);
        edit.setPreservedHeaderNames(java.util.List.of("X-Keep"));
        edit.setHeaders(Map.of("X-New", "replacement"));
        request.setMonitorConfig(edit);
        var saved =
                JSONUtil.toBean(
                        cipher.decrypt(service.protect(request, existing)),
                        com.ifelio.ijob.core.resource.factory.monitor.MonitorServerResourceConfig
                                .class);
        assertEquals("https://monitor.invalid", saved.getEndpoint());
        assertEquals(24000, saved.getReadTimeout());
        assertEquals("LINUX", saved.getOsType().name());
        assertEquals("1.0", saved.getAgentVersion());
        assertEquals("monitor-secret", saved.getToken());
        assertEquals(Map.of("X-Keep", "hidden", "X-New", "replacement"), saved.getHeaders());
        var detail = assertInstanceOf(ResourceConfigView.Monitor.class, service.view(existing));
        assertEquals("https://monitor.invalid", detail.getEndpoint());
        assertTrue(detail.isTokenConfigured());
        assertFalse(JSONUtil.toJsonStr(detail).contains("monitor-secret"));
        assertFalse(JSONUtil.toJsonStr(detail).contains("hidden"));
        edit.setClearToken(true);
        edit.setPreservedHeaderNames(java.util.List.of());
        edit.setHeaders(Map.of());
        saved =
                JSONUtil.toBean(
                        cipher.decrypt(service.protect(request, existing)),
                        com.ifelio.ijob.core.resource.factory.monitor.MonitorServerResourceConfig
                                .class);
        assertTrue(saved.getToken() == null || saved.getToken().isEmpty());
        assertTrue(saved.getHeaders().isEmpty());
    }

    @Test
    void monitorRejectsAmbiguousBaseUrlsAndCaseDuplicateHeaders() {
        var request = monitorRequest();
        for (String suffix : new String[] {"?region=one", "#fragment"}) {
            request.getMonitorConfig().setEndpoint("https://monitor.invalid" + suffix);
            assertThrows(BusinessException.class, () -> service.protect(request, null));
        }
        request.getMonitorConfig().setEndpoint("https://monitor.invalid/base");
        request.getMonitorConfig().setHeaders(Map.of("X-Key", "one", "x-key", "two"));
        assertThrows(BusinessException.class, () -> service.protect(request, null));
    }

    private static final String PASSWORD = "database-secret";

    private final ResourceConfigCipher cipher = cipher();
    private final ResourceConfigService service = new ResourceConfigService(cipher);

    @Test
    void serviceEditPreservesHiddenConfigurationAndPatchesHeadersExplicitly() {
        var request = serviceRequest();
        var config = request.getServiceConfig();
        config.setHeaders(Map.of("X-Key", "hidden-key", "X-Remove", "remove"));
        config.setReadTimeout(45);
        config.setAuthType("OAUTH2_CLIENT_CREDENTIALS");
        var oauth = new ResourceConfigRequest.OAuth2ClientCredentials();
        oauth.setTokenEndpoint("https://auth.example.invalid/token");
        oauth.setClientId("hidden-id");
        oauth.setClientSecret("hidden-secret");
        oauth.setScopes("hidden-scope");
        config.setOauth2(oauth);
        var existing = resource(ResourceType.SERVICE, service.protect(request, null));
        var edit = new ResourceConfigRequest.Service();
        edit.setPreserveExisting(true);
        edit.setPreservedHeaderNames(java.util.List.of("X-Key"));
        edit.setHeaders(Map.of("X-New", "new"));
        request.setServiceConfig(edit);
        var saved =
                JSONUtil.toBean(
                        cipher.decrypt(service.protect(request, existing)),
                        ServiceResourceConfig.class);
        assertEquals(config.getEndpoint(), saved.getEndpoint());
        assertEquals(45, saved.getReadTimeout());
        assertEquals("hidden-id", saved.getOauth2().getClientId());
        assertEquals("hidden-secret", saved.getOauth2().getClientSecret());
        assertEquals(Map.of("X-Key", "hidden-key", "X-New", "new"), saved.getHeaders());
        var detail = assertInstanceOf(ResourceConfigView.Service.class, service.view(existing));
        assertEquals(config.getEndpoint(), detail.getEndpoint());
        assertEquals(oauth.getTokenEndpoint(), detail.getOauth2TokenEndpoint());
        assertEquals(oauth.getClientId(), detail.getOauth2ClientId());
        assertEquals(oauth.getScopes(), detail.getOauth2Scopes());
        var view = JSONUtil.toJsonStr(detail);
        for (String secret : java.util.List.of("hidden-key", "hidden-secret"))
            assertFalse(view.contains(secret));
        var change = new ResourceConfigRequest.OAuth2ClientCredentials();
        change.setClearScopes(true);
        edit.setOauth2(change);
        saved =
                JSONUtil.toBean(
                        cipher.decrypt(service.protect(request, existing)),
                        ServiceResourceConfig.class);
        assertEquals(null, saved.getOauth2().getScopes());
        edit.setHeaders(Map.of("x-key", "collision"));
        assertThrows(BusinessException.class, () -> service.protect(request, existing));
        edit.setHeaders(Map.of());
        edit.setAuthType("NONE");
        edit.setOauth2(null);
        saved =
                JSONUtil.toBean(
                        cipher.decrypt(service.protect(request, existing)),
                        ServiceResourceConfig.class);
        assertEquals(null, saved.getOauth2());
        edit.setProtocol("WEB_SERVICE");
        assertThrows(BusinessException.class, () -> service.protect(request, existing));
    }

    @Test
    void soapConfigurationIsEditableAndOmittedFieldsRemainCompatible() {
        var request = serviceRequest();
        request.getServiceConfig().setProtocol("WEB_SERVICE");
        var soap = new ResourceConfigRequest.WebService();
        soap.setWsdl("https://example.invalid/service?wsdl");
        soap.setNamespace("urn:demo");
        soap.setServiceName("Orders");
        soap.setPortName("OrdersPort");
        request.getServiceConfig().setWebService(soap);
        var existing = resource(ResourceType.SERVICE, service.protect(request, null));
        var edit = new ResourceConfigRequest.Service();
        edit.setPreserveExisting(true);
        request.setServiceConfig(edit);
        var saved =
                JSONUtil.toBean(
                        cipher.decrypt(service.protect(request, existing)),
                        ServiceResourceConfig.class);
        assertEquals("OrdersPort", saved.getWebService().getPortName());
        var view = assertInstanceOf(ResourceConfigView.Service.class, service.view(existing));
        assertEquals(soap.getWsdl(), view.getWsdl());
        assertEquals(soap.getNamespace(), view.getNamespace());
        assertEquals(soap.getServiceName(), view.getServiceName());
        assertEquals(soap.getPortName(), view.getPortName());
        edit.setEndpoint("https://example.invalid/updated");
        soap.setNamespace("urn:updated");
        edit.setWebService(soap);
        saved =
                JSONUtil.toBean(
                        cipher.decrypt(service.protect(request, existing)),
                        ServiceResourceConfig.class);
        assertEquals("https://example.invalid/updated", saved.getEndpoint());
        assertEquals("urn:updated", saved.getWebService().getNamespace());

        assertThrows(BusinessException.class, () -> service.protect(request, null));
    }

    @Test
    void managedSshTrustRequiresAKeyAndDoesNotCarryItToAnotherEndpoint() {
        byte[] type = "ssh-ed25519".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        String key =
                Base64.getEncoder()
                        .encodeToString(
                                java.nio.ByteBuffer.allocate(4 + type.length + 4 + 32)
                                        .putInt(type.length)
                                        .put(type)
                                        .putInt(32)
                                        .put(new byte[32])
                                        .array());
        var config = new ResourceConfigRequest.Ssh();
        config.setHost("runner.internal");
        config.setUsername("runner");
        config.setPassword("password");
        config.setHostKeyVerification("MANAGED");
        var request = base(ResourceType.SSH);
        request.setSshConfig(config);
        assertThrows(BusinessException.class, () -> service.protect(request, null));
        config.setHostKey(key);
        var existing = resource(ResourceType.SSH, service.protect(request, null));
        var view = assertInstanceOf(ResourceConfigView.Ssh.class, service.view(existing));
        assertEquals("MANAGED", view.getHostKeyVerification());
        assertEquals("ssh-ed25519", view.getHostKeyType());
        assertTrue(view.getHostKeyFingerprint().startsWith("SHA256:"));
        assertFalse(JSONUtil.toJsonStr(view).contains(key));
        assertEquals(null, view.getKnownHosts());
        config.setHostKey(null);
        config.setPassword(null);
        config.setPreserveExisting(true);
        var saved =
                JSONUtil.toBean(
                        cipher.decrypt(service.protect(request, existing)),
                        SshResourceConfig.class);
        assertEquals(key, saved.getHostKey());
        config.setHost("another.internal");
        assertThrows(BusinessException.class, () -> service.protect(request, existing));
        config.setHost("runner.internal");
        config.setPort(2222);
        assertThrows(BusinessException.class, () -> service.protect(request, existing));
        config.setHostKey(key);
        assertEquals(
                2222,
                JSONUtil.toBean(
                                cipher.decrypt(service.protect(request, existing)),
                                SshResourceConfig.class)
                        .getPort());
        config.setHostKeyVerification("KNOWN_HOSTS");
        assertThrows(BusinessException.class, () -> service.protect(request, existing));
        config.setKnownHosts("/keys/known_hosts");
        saved =
                JSONUtil.toBean(
                        cipher.decrypt(service.protect(request, existing)),
                        SshResourceConfig.class);
        assertEquals(null, saved.getHostKey());
        assertEquals("KNOWN_HOSTS", saved.getHostKeyVerification());
        config.setHostKeyVerification("DISABLED");
        assertThrows(BusinessException.class, () -> service.protect(request, existing));
    }

    @Test
    void explicitPreservationShouldKeepHiddenDbFieldsButNeverCrossDatabaseTypes() {
        ResourceUpsertRequest create = dbRequest(PASSWORD);
        create.getDbConfig().setSchema("business");
        Resource existing = resource(ResourceType.DB, service.protect(create, null));
        ResourceUpsertRequest edit = dbRequest(null);
        edit.getDbConfig().setJdbcUrl(null);
        edit.getDbConfig().setUsername(null);
        edit.getDbConfig().setSchema(null);
        assertThrows(BusinessException.class, () -> service.protect(edit, existing));
        edit.getDbConfig().setPreserveExisting(true);
        DbResourceConfig saved =
                JSONUtil.toBean(
                        cipher.decrypt(service.protect(edit, existing)), DbResourceConfig.class);
        DbResourceConfig original =
                JSONUtil.toBean(cipher.decrypt(existing.getConfig()), DbResourceConfig.class);
        assertEquals(original.getJdbcUrl(), saved.getJdbcUrl());
        assertEquals(original.getUsername(), saved.getUsername());
        assertEquals(PASSWORD, saved.getPassword());
        assertEquals("business", saved.getSchema());
        edit.getDbConfig().setSchema("");
        assertEquals(
                null,
                JSONUtil.toBean(
                                cipher.decrypt(service.protect(edit, existing)),
                                DbResourceConfig.class)
                        .getSchema());
        assertThrows(BusinessException.class, () -> service.protect(edit, null));
        edit.getDbConfig().setJdbcUrl("");
        assertThrows(BusinessException.class, () -> service.protect(edit, existing));
        edit.getDbConfig().setJdbcUrl(null);
        edit.getDbConfig().setDbType("POSTGRESQL");
        assertThrows(BusinessException.class, () -> service.protect(edit, existing));
    }

    @Test
    void dbConfigShouldRevealConnectionFieldsButNeverReturnPassword() {
        ResourceUpsertRequest request = dbRequest(PASSWORD);

        String protectedConfig = service.protect(request, null);
        Resource resource = resource(ResourceType.DB, protectedConfig);
        ResourceConfigView.Db view =
                assertInstanceOf(ResourceConfigView.Db.class, service.view(resource));

        assertTrue(cipher.isProtected(protectedConfig));
        assertFalse(protectedConfig.contains(PASSWORD));
        assertEquals("MYSQL", view.getDbType());
        assertTrue(view.isJdbcUrlConfigured());
        assertTrue(view.isUsernameConfigured());
        assertTrue(view.isPasswordConfigured());
        assertEquals(request.getDbConfig().getJdbcUrl(), view.getJdbcUrl());
        assertEquals(request.getDbConfig().getUsername(), view.getUsername());
        String serialized = JSONUtil.toJsonStr(view);
        assertFalse(serialized.contains(PASSWORD));
        assertFalse(JSONUtil.parseObj(serialized).containsKey("password"));
    }

    @Test
    void updateShouldRetainOrExplicitlyClearExistingSecret() {
        String existingConfig = service.protect(dbRequest(PASSWORD), null);
        Resource existing = resource(ResourceType.DB, existingConfig);
        ResourceUpsertRequest retain = dbRequest(null);

        String retainedConfig = service.protect(retain, existing);
        DbResourceConfig retained =
                JSONUtil.toBean(cipher.decrypt(retainedConfig), DbResourceConfig.class);
        assertEquals(PASSWORD, retained.getPassword());

        ResourceConfigRequest.Db clearConfig = retain.getDbConfig();
        clearConfig.setClearPassword(true);
        String clearedConfig = service.protect(retain, existing);
        DbResourceConfig cleared =
                JSONUtil.toBean(cipher.decrypt(clearedConfig), DbResourceConfig.class);
        assertEquals(null, cleared.getPassword());
    }

    @Test
    void shouldRejectMismatchedOrMultipleTypeConfigs() {
        ResourceUpsertRequest request = dbRequest(PASSWORD);
        request.setType(ResourceType.KAFKA);
        ResourceConfigRequest.Kafka kafka = new ResourceConfigRequest.Kafka();
        kafka.setBootstrapServers("127.0.0.1:9092");
        request.setKafkaConfig(kafka);

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.protect(request, null));

        assertEquals(
                "Exactly one configuration matching the resource type is required",
                exception.getMessage());
    }

    @Test
    void shouldValidateAndProtectEverySupportedResourceType() {
        assertTrue(cipher.isProtected(service.protect(localFileRequest(), null)));
        assertTrue(cipher.isProtected(service.protect(serviceRequest(), null)));
        assertTrue(cipher.isProtected(service.protect(rabbitRequest(), null)));
        assertTrue(cipher.isProtected(service.protect(kafkaRequest(), null)));
        assertTrue(cipher.isProtected(service.protect(monitorRequest(), null)));
    }

    @Test
    void serviceTimeoutsShouldBeProtectedValidatedAndVisible() {
        ResourceUpsertRequest request = serviceRequest();
        ResourceConfigRequest.Service config = request.getServiceConfig();
        config.setConnectTimeout(4);
        config.setReadTimeout(15);
        config.setWriteTimeout(12);
        config.setCallTimeout(25);

        String protectedConfig = service.protect(request, null);
        ResourceConfigView.Service view =
                assertInstanceOf(
                        ResourceConfigView.Service.class,
                        service.view(resource(ResourceType.SERVICE, protectedConfig)));

        assertEquals(4, view.getConnectTimeout());
        assertEquals(15, view.getReadTimeout());
        assertEquals(12, view.getWriteTimeout());
        assertEquals(25, view.getCallTimeout());

        config.setCallTimeout(0);
        assertThrows(BusinessException.class, () -> service.protect(request, null));
    }

    @Test
    void sftpShouldRequireAndRetainKnownHostsFile() {
        ResourceUpsertRequest request = sftpFileRequest(null);
        request.getFileConfig().setHostKeyVerification("KNOWN_HOSTS");
        BusinessException missing =
                assertThrows(BusinessException.class, () -> service.protect(request, null));
        assertEquals("SFTP resources require a knownHosts file", missing.getMessage());

        request.getFileConfig().setKnownHosts("E:/keys/known_hosts");
        String protectedConfig = service.protect(request, null);
        Resource existing = resource(ResourceType.FILE, protectedConfig);
        FileResourceConfig stored =
                JSONUtil.toBean(cipher.decrypt(protectedConfig), FileResourceConfig.class);
        ResourceConfigView.File view =
                assertInstanceOf(ResourceConfigView.File.class, service.view(existing));

        assertEquals("E:/keys/known_hosts", stored.getKnownHosts());
        assertTrue(view.isKnownHostsConfigured());

        request.getFileConfig().setKnownHosts(null);
        String retainedConfig = service.protect(request, existing);
        FileResourceConfig retained =
                JSONUtil.toBean(cipher.decrypt(retainedConfig), FileResourceConfig.class);
        assertEquals("E:/keys/known_hosts", retained.getKnownHosts());
    }

    @Test
    void sshPreservationRetainsFieldsAndSecretsAndClearsOnlyExplicitly() {
        ResourceConfigRequest.Ssh config = new ResourceConfigRequest.Ssh();
        config.setHost("ssh.example.invalid");
        config.setUsername("runner");
        config.setPort(2222);
        config.setConnectTimeout(12345);
        config.setPassword("ssh-secret");
        config.setPrivateKey("/keys/private");
        config.setPassphrase("key-secret");
        config.setKnownHosts("/keys/known_hosts");
        ResourceUpsertRequest request = base(ResourceType.SSH);
        request.setSshConfig(config);
        Resource existing = resource(ResourceType.SSH, service.protect(request, null));
        ResourceConfigRequest.Ssh edit = new ResourceConfigRequest.Ssh();
        request.setSshConfig(edit);
        assertThrows(BusinessException.class, () -> service.protect(request, existing));
        edit.setPreserveExisting(true);
        SshResourceConfig saved =
                JSONUtil.toBean(
                        cipher.decrypt(service.protect(request, existing)),
                        SshResourceConfig.class);
        assertEquals("ssh.example.invalid", saved.getHost());
        assertEquals("runner", saved.getUsername());
        assertEquals(2222, saved.getPort());
        assertEquals(12345, saved.getConnectTimeout());
        assertEquals("/keys/private", saved.getPrivateKey());
        edit.setClearPrivateKey(true);
        assertThrows(BusinessException.class, () -> service.protect(request, existing));
        edit.setClearPassphrase(true);
        saved =
                JSONUtil.toBean(
                        cipher.decrypt(service.protect(request, existing)),
                        SshResourceConfig.class);
        assertEquals("ssh-secret", saved.getPassword());
        assertEquals(null, saved.getPrivateKey());
        edit.setClearPassword(true);
        assertThrows(BusinessException.class, () -> service.protect(request, existing));
    }

    @Test
    void shouldRejectSensitiveJdbcQueryAndHeaderInjection() {
        ResourceUpsertRequest db = dbRequest(PASSWORD);
        db.getDbConfig().setJdbcUrl("jdbc:mysql://127.0.0.1/demo?password=leak");
        assertThrows(BusinessException.class, () -> service.protect(db, null));

        ResourceUpsertRequest http = serviceRequest();
        http.getServiceConfig()
                .setHeaders(Map.of("Authorization", "Bearer value\r\nInjected: true"));
        assertThrows(BusinessException.class, () -> service.protect(http, null));
    }

    private ResourceUpsertRequest dbRequest(String password) {
        ResourceConfigRequest.Db config = new ResourceConfigRequest.Db();
        config.setDbType("MYSQL");
        config.setJdbcUrl("jdbc:mysql://127.0.0.1/demo");
        config.setUsername("demo");
        config.setPassword(password);

        ResourceUpsertRequest request = base(ResourceType.DB);
        request.setDbConfig(config);
        return request;
    }

    private ResourceUpsertRequest localFileRequest() {
        ResourceConfigRequest.File config = new ResourceConfigRequest.File();
        config.setProtocol("LOCAL");
        config.setBasePath("E:/safe-root");
        ResourceUpsertRequest request = base(ResourceType.FILE);
        request.setFileConfig(config);
        return request;
    }

    @Test
    void oauth2ClientCredentialsShouldBeProtectedRetainedAndMasked() {
        ResourceUpsertRequest request = serviceRequest();
        ResourceConfigRequest.Service serviceConfig = request.getServiceConfig();
        serviceConfig.setHeaders(Map.of("X-Resource", "safe"));
        serviceConfig.setAuthType("OAUTH2_CLIENT_CREDENTIALS");
        ResourceConfigRequest.OAuth2ClientCredentials oauth2 =
                new ResourceConfigRequest.OAuth2ClientCredentials();
        oauth2.setTokenEndpoint("https://auth.example.invalid/token");
        oauth2.setClientId("client-id");
        oauth2.setClientSecret("client-secret");
        oauth2.setScopes("orders.read");
        serviceConfig.setOauth2(oauth2);

        String protectedConfig = service.protect(request, null);
        Resource existing = resource(ResourceType.SERVICE, protectedConfig);
        ResourceConfigView.Service view =
                assertInstanceOf(ResourceConfigView.Service.class, service.view(existing));
        ServiceResourceConfig stored =
                JSONUtil.toBean(cipher.decrypt(protectedConfig), ServiceResourceConfig.class);

        assertEquals("client-secret", stored.getOauth2().getClientSecret());
        assertEquals("OAUTH2_CLIENT_CREDENTIALS", view.getAuthType());
        assertTrue(view.isOauth2ClientSecretConfigured());
        oauth2.setClientSecret(null);
        ServiceResourceConfig retained =
                JSONUtil.toBean(
                        cipher.decrypt(service.protect(request, existing)),
                        ServiceResourceConfig.class);
        assertEquals("client-secret", retained.getOauth2().getClientSecret());

        serviceConfig.setHeaders(Map.of("authorization", "override"));
        assertThrows(BusinessException.class, () -> service.protect(request, existing));
    }

    @Test
    void s3CompatibleFileResourceShouldProtectCredentials() {
        ResourceConfigRequest.File config = new ResourceConfigRequest.File();
        config.setProtocol("S3_COMPATIBLE");
        config.setEndpoint("https://minio.example.invalid");
        config.setRegion("us-east-1");
        config.setBucket("ijob-files");
        config.setBasePath("incoming");
        config.setAccessKey("access-key");
        config.setSecretKey("secret-key");
        config.setPathStyleAccess(true);
        ResourceUpsertRequest request = base(ResourceType.FILE);
        request.setFileConfig(config);

        String protectedConfig = service.protect(request, null);
        FileResourceConfig stored =
                JSONUtil.toBean(cipher.decrypt(protectedConfig), FileResourceConfig.class);
        ResourceConfigView.File view =
                assertInstanceOf(
                        ResourceConfigView.File.class,
                        service.view(resource(ResourceType.FILE, protectedConfig)));

        assertEquals("secret-key", stored.getSecretKey());
        assertTrue(view.isSecretKeyConfigured());
        assertTrue(view.isPathStyleAccess());

        config.setEndpoint("https://minio.example.invalid/api");
        assertThrows(BusinessException.class, () -> service.protect(request, null));
    }

    @Test
    void sshResourceShouldRequireKnownHostsAndMaskCredentials() {
        ResourceConfigRequest.Ssh config = new ResourceConfigRequest.Ssh();
        config.setHost("ssh.example.invalid");
        config.setUsername("runner");
        config.setPassword("ssh-secret");
        config.setKnownHosts("E:/keys/known_hosts");
        ResourceUpsertRequest request = base(ResourceType.SSH);
        request.setSshConfig(config);

        String protectedConfig = service.protect(request, null);
        SshResourceConfig stored =
                JSONUtil.toBean(cipher.decrypt(protectedConfig), SshResourceConfig.class);
        ResourceConfigView.Ssh view =
                assertInstanceOf(
                        ResourceConfigView.Ssh.class,
                        service.view(resource(ResourceType.SSH, protectedConfig)));

        assertEquals("ssh-secret", stored.getPassword());
        assertTrue(view.isPasswordConfigured());
        assertTrue(view.isKnownHostsConfigured());
        assertEquals("ssh.example.invalid", view.getHost());
        assertEquals("runner", view.getUsername());
        assertEquals("E:/keys/known_hosts", view.getKnownHosts());
        assertFalse(JSONUtil.toJsonStr(view).contains("ssh-secret"));
    }

    private ResourceUpsertRequest sftpFileRequest(String knownHosts) {
        ResourceConfigRequest.File config = new ResourceConfigRequest.File();
        config.setProtocol("SFTP");
        config.setHost("sftp.invalid");
        config.setUsername("demo");
        config.setPassword("sftp-secret");
        config.setKnownHosts(knownHosts);
        config.setBasePath("/safe-root");
        ResourceUpsertRequest request = base(ResourceType.FILE);
        request.setFileConfig(config);
        return request;
    }

    private ResourceUpsertRequest serviceRequest() {
        ResourceConfigRequest.Service config = new ResourceConfigRequest.Service();
        config.setProtocol("HTTP");
        config.setEndpoint("https://example.invalid/api");
        config.setHeaders(Map.of("Authorization", "Bearer secret"));
        ResourceUpsertRequest request = base(ResourceType.SERVICE);
        request.setServiceConfig(config);
        return request;
    }

    private ResourceUpsertRequest rabbitRequest() {
        ResourceConfigRequest.Rabbit config = new ResourceConfigRequest.Rabbit();
        config.setHost("127.0.0.1");
        config.setUsername("demo");
        config.setPassword("rabbit-secret");
        ResourceUpsertRequest request = base(ResourceType.RABBITMQ);
        request.setRabbitConfig(config);
        return request;
    }

    private ResourceUpsertRequest kafkaRequest() {
        ResourceConfigRequest.Kafka config = new ResourceConfigRequest.Kafka();
        config.setBootstrapServers("127.0.0.1:9092");
        ResourceUpsertRequest request = base(ResourceType.KAFKA);
        request.setKafkaConfig(config);
        return request;
    }

    private ResourceUpsertRequest monitorRequest() {
        ResourceConfigRequest.Monitor config = new ResourceConfigRequest.Monitor();
        config.setProtocol("HTTPS");
        config.setEndpoint("https://monitor.invalid");
        config.setToken("monitor-secret");
        ResourceUpsertRequest request = base(ResourceType.MONITOR_SERVER);
        request.setMonitorConfig(config);
        return request;
    }

    private ResourceUpsertRequest base(ResourceType type) {
        ResourceUpsertRequest request = new ResourceUpsertRequest();
        request.setCode("resource-code");
        request.setName("Resource Name");
        request.setType(type);
        return request;
    }

    private Resource resource(ResourceType type, String config) {
        Resource resource = new Resource();
        resource.setId(7L);
        resource.setType(type);
        resource.setConfig(config);
        return resource;
    }

    private ResourceConfigCipher cipher() {
        AppProperties properties = new AppProperties();
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) 9);
        properties.getSecurity().getAes().setKey(Base64.getEncoder().encodeToString(key));
        properties.getSecurity().getAes().setKeyId("test");
        return new ResourceConfigCipher(properties);
    }
}
