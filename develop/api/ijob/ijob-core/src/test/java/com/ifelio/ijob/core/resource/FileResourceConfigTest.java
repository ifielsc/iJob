package com.ifelio.ijob.core.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.json.JSONUtil;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.contract.enums.ResourceType;
import com.ifelio.ijob.contract.resource.ResourceConfigRequest;
import com.ifelio.ijob.contract.resource.ResourceConfigView;
import com.ifelio.ijob.contract.resource.ResourceUpsertRequest;
import com.ifelio.ijob.core.resource.factory.file.FileResourceConfig;
import com.ifelio.ijob.core.resource.security.ResourceConfigCipher;
import com.ifelio.ijob.infra.entity.resource.Resource;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

class FileResourceConfigTest {
    private final ResourceConfigCipher cipher;
    private final ResourceConfigService service;

    FileResourceConfigTest() {
        AppProperties properties = new AppProperties();
        properties.getSecurity().getAes().setKeyId("file-test");
        properties.getSecurity().getAes().setKey(Base64.getEncoder().encodeToString(new byte[32]));
        cipher = new ResourceConfigCipher(properties);
        service = new ResourceConfigService(cipher);
    }

    private String key() {
        byte[] type = "ssh-ed25519".getBytes(StandardCharsets.US_ASCII);
        return Base64.getEncoder()
                .encodeToString(
                        ByteBuffer.allocate(4 + type.length + 4 + 32)
                                .putInt(type.length)
                                .put(type)
                                .putInt(32)
                                .put(new byte[32])
                                .array());
    }

    private ResourceUpsertRequest request(String protocol) {
        var request = new ResourceUpsertRequest();
        request.setType(ResourceType.FILE);
        var file = new ResourceConfigRequest.File();
        file.setProtocol(protocol);
        file.setBasePath("/");
        request.setFileConfig(file);
        return request;
    }

    private Resource resource(ResourceUpsertRequest request) {
        var resource = new Resource();
        resource.setType(ResourceType.FILE);
        resource.setConfig(service.protect(request, null));
        return resource;
    }

    @Test
    void managedIdentityDoesNotNeedFileAndChangesRequireNewTrust() {
        var request = request("SFTP");
        var file = request.getFileConfig();
        file.setHost("files.example.invalid");
        file.setUsername("fixture");
        file.setPassword("fixture-secret");
        file.setHostKeyVerification("MANAGED");
        file.setHostKey(key());
        var existing = resource(request);
        var view = (ResourceConfigView.File) service.view(existing);
        assertEquals("MANAGED", view.getHostKeyVerification());
        assertTrue(view.getHostKeyFingerprint().startsWith("SHA256:"));
        assertFalse(view.isKnownHostsConfigured());
        assertEquals("/", view.getBasePath());
        assertEquals(file.getHost(), view.getHost());
        assertFalse(JSONUtil.toJsonStr(view).contains("fixture-secret"));
        assertFalse(JSONUtil.toJsonStr(view).contains(key()));
        var edit = new ResourceConfigRequest.File();
        edit.setPreserveExisting(true);
        request.setFileConfig(edit);
        var retained =
                JSONUtil.toBean(
                        cipher.decrypt(service.protect(request, existing)),
                        FileResourceConfig.class);
        assertEquals(key(), retained.getHostKey());
        assertEquals("fixture-secret", retained.getPassword());
        edit.setPort(2222);
        assertThrows(BusinessException.class, () -> service.protect(request, existing));
        edit.setHostKey(key());
        assertEquals(
                2222,
                JSONUtil.toBean(
                                cipher.decrypt(service.protect(request, existing)),
                                FileResourceConfig.class)
                        .getPort());
        edit.setClearPassword(true);
        assertThrows(BusinessException.class, () -> service.protect(request, existing));
        edit.setPrivateKey("/keys/client");
        assertNull(
                JSONUtil.toBean(
                                cipher.decrypt(service.protect(request, existing)),
                                FileResourceConfig.class)
                        .getPassword());
    }

    @Test
    void objectStoragePreservesCredentialsAndExplicitlyClearsOptionalToken() {
        var request = request("S3_COMPATIBLE");
        var file = request.getFileConfig();
        file.setRegion("us-east-1");
        file.setBucket("fixture-bucket");
        file.setEndpoint("http://localhost:9000");
        file.setAccessKey("fixture-access");
        file.setSecretKey("fixture-secret");
        file.setSessionToken("fixture-token");
        var existing = resource(request);
        var view = (ResourceConfigView.File) service.view(existing);
        assertEquals("fixture-bucket", view.getBucket());
        assertFalse(JSONUtil.toJsonStr(view).contains("fixture-secret"));
        assertFalse(JSONUtil.toJsonStr(view).contains("fixture-access"));
        var edit = new ResourceConfigRequest.File();
        edit.setPreserveExisting(true);
        edit.setClearSessionToken(true);
        request.setFileConfig(edit);
        var saved =
                JSONUtil.toBean(
                        cipher.decrypt(service.protect(request, existing)),
                        FileResourceConfig.class);
        assertNull(saved.getSessionToken());
        assertEquals("fixture-secret", saved.getSecretKey());
        assertEquals(file.getEndpoint(), saved.getEndpoint());
        edit.setClearSecretKey(true);
        assertThrows(BusinessException.class, () -> service.protect(request, existing));
        edit.setProtocol("FTP");
        edit.setHost("files.example.invalid");
        edit.setUsername("fixture");
        edit.setBasePath("/");
        assertThrows(BusinessException.class, () -> service.protect(request, existing));
    }

    @Test
    void localRootPreservesOrdinaryFieldsWithoutRemoteAuthentication() {
        var request = request("LOCAL");
        var existing = resource(request);
        var edit = new ResourceConfigRequest.File();
        edit.setPreserveExisting(true);
        request.setFileConfig(edit);
        assertEquals(
                "/",
                JSONUtil.toBean(
                                cipher.decrypt(service.protect(request, existing)),
                                FileResourceConfig.class)
                        .getBasePath());
        edit.setBasePath("");
        assertThrows(BusinessException.class, () -> service.protect(request, existing));
    }
}
