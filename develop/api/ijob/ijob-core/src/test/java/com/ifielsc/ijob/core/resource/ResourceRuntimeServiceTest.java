// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.json.JSONUtil;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.contract.resource.ResourceConnectionTestDTO;
import com.ifielsc.ijob.core.resource.factory.ResourceClientFactory;
import com.ifielsc.ijob.core.resource.factory.db.DbResourceConfig;
import com.ifielsc.ijob.core.resource.factory.db.DbType;
import com.ifielsc.ijob.core.resource.security.ResourceConfigCipher;
import com.ifielsc.ijob.infra.entity.resource.Resource;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

class ResourceRuntimeServiceTest {
    @Test
    void duplicateFactoryRegistrationIsRejectedBeforeClientCreation() {
        var first = new RecordingFactory();
        var second = new RecordingFactory();
        var runtime = new ResourceRuntimeService(null, null, List.of(first, second));
        assertThrows(IllegalStateException.class, runtime::init);
        assertEquals(0, first.createCount);
        assertEquals(0, second.createCount);
    }

    @Test
    void incompatibleExclusiveClientIsClosedBeforeFailureEscapes() {
        var cipher = cipher();
        var factory = new RecordingFactory();
        var runtime = runtime(enabledResource(cipher), cipher, factory);
        try {
            assertThrows(
                    BusinessException.class,
                    () -> runtime.leaseClient(7L, ResourceType.DB, javax.sql.DataSource.class));
            assertEquals(1, factory.lastClient.closeCount);
        } finally {
            runtime.destroy();
        }
    }

    @Test
    void shouldDecryptOnceCacheAndCloseClientOnRefresh() {
        ResourceConfigCipher cipher = cipher();
        Resource resource = enabledResource(cipher);
        RecordingFactory factory = new RecordingFactory();
        ResourceRuntimeService runtime = runtime(resource, cipher, factory);

        TrackingClient first = cachedClient(runtime, 7L, ResourceType.DB, TrackingClient.class);
        TrackingClient cached = cachedClient(runtime, 7L, ResourceType.DB, TrackingClient.class);

        assertSame(first, cached);
        assertEquals(1, factory.createCount);
        assertEquals("database-secret", factory.passwordSeen);
        runtime.refresh(7L);
        assertTrue(first.closed);

        TrackingClient second = cachedClient(runtime, 7L, ResourceType.DB, TrackingClient.class);
        assertFalse(second.closed);
        assertEquals(2, factory.createCount);
        runtime.destroy();
        assertTrue(second.closed);
    }

    @Test
    void leasedClientShouldBeDedicatedAndCloseExactlyOnce() throws Exception {
        ResourceConfigCipher cipher = cipher();
        RecordingFactory factory = new RecordingFactory();
        ResourceRuntimeService runtime = runtime(enabledResource(cipher), cipher, factory);

        ResourceClientLease<TrackingClient> first =
                runtime.leaseClient(7L, ResourceType.DB, TrackingClient.class);
        ResourceClientLease<TrackingClient> second =
                runtime.leaseClient(7L, ResourceType.DB, TrackingClient.class);

        assertFalse(first.getClient() == second.getClient());
        assertEquals(2, factory.createCount);
        first.close();
        first.close();
        assertEquals(1, first.getClient().closeCount);
        assertFalse(second.getClient().closed);
        runtime.destroy();
        assertFalse(second.getClient().closed);
        second.close();
        assertEquals(1, second.getClient().closeCount);
    }

    @Test
    void shouldRejectDisabledAndNonCacheableResourceTypes() {
        ResourceConfigCipher cipher = cipher();
        Resource resource = enabledResource(cipher);
        resource.setEnabled(false);
        RecordingFactory factory = new RecordingFactory();
        ResourceRuntimeService runtime = runtime(resource, cipher, factory);

        BusinessException disabled =
                assertThrows(
                        BusinessException.class,
                        () -> cachedClient(runtime, 7L, ResourceType.DB, TrackingClient.class));
        assertEquals("Resource is disabled", disabled.getMessage());
        assertEquals(0, factory.createCount);

        resource.setEnabled(true);
        BusinessException nonCacheable =
                assertThrows(
                        BusinessException.class,
                        () -> cachedClient(runtime, 7L, ResourceType.FILE, TrackingClient.class));
        assertEquals(
                "This resource type requires an exclusive client lease", nonCacheable.getMessage());
        runtime.destroy();
    }

    @Test
    void connectionTestShouldUseEphemeralClientAndAlwaysCloseIt() {
        ResourceConfigCipher cipher = cipher();
        RecordingFactory factory = new RecordingFactory();
        ResourceRuntimeService runtime = runtime(enabledResource(cipher), cipher, factory);

        ResourceConnectionTestDTO result = runtime.testConnection(7L);

        assertEquals(7L, result.getResourceId());
        assertEquals(ResourceType.DB, result.getType());
        assertTrue(result.isSuccessful());
        assertEquals(1, factory.verifyCount);
        assertTrue(factory.lastClient.closed);
        runtime.destroy();
    }

    @Test
    void connectionFailureShouldExposeOnlyFixedSafeMessageAndCloseClient() {
        ResourceConfigCipher cipher = cipher();
        RecordingFactory factory = new RecordingFactory();
        factory.failVerification = true;
        ResourceRuntimeService runtime = runtime(enabledResource(cipher), cipher, factory);

        BusinessException exception =
                assertThrows(BusinessException.class, () -> runtime.testConnection(7L));

        assertEquals("Resource connection test failed", exception.getMessage());
        assertTrue(factory.lastClient.closed);
        runtime.destroy();
    }

    @Test
    void committedChangeEventShouldInvalidateAndCloseCachedClient() {
        ResourceConfigCipher cipher = cipher();
        RecordingFactory factory = new RecordingFactory();
        ResourceRuntimeService runtime = runtime(enabledResource(cipher), cipher, factory);
        TrackingClient client = cachedClient(runtime, 7L, ResourceType.DB, TrackingClient.class);

        runtime.onResourceChanged(new ResourceCacheInvalidationEvent(7L));

        assertTrue(client.closed);
        runtime.destroy();
    }

    @Test
    void unchangedClientShouldSurviveFreshnessChecks() throws Exception {
        ResourceConfigCipher cipher = cipher();
        RecordingFactory factory = new RecordingFactory();
        AppProperties properties = new AppProperties();
        properties.getCacheConsistency().setMaxStaleness(Duration.ofMillis(1));
        ResourceRuntimeService runtime =
                new ResourceRuntimeService(
                        new StubResourceService(enabledResource(cipher)),
                        new ResourceConfigService(cipher),
                        List.of(factory),
                        properties);
        runtime.init();

        TrackingClient first = cachedClient(runtime, 7L, ResourceType.DB, TrackingClient.class);
        Thread.sleep(25L);
        TrackingClient reloaded = cachedClient(runtime, 7L, ResourceType.DB, TrackingClient.class);

        assertSame(first, reloaded);
        assertFalse(first.closed);
        assertEquals(1, factory.createCount);
        runtime.destroy();
    }

    private <T> T cachedClient(
            ResourceRuntimeService runtime, Long id, ResourceType type, Class<T> clientType) {
        try (var lease = runtime.leaseCachedClient(id, type, clientType)) {
            return lease.getClient();
        }
    }

    @Test
    void freshnessCheckReplacesChangedConfigButRetainsActiveOldLease() throws Exception {
        var cipher = cipher();
        var resource = enabledResource(cipher);
        var factory = new RecordingFactory();
        var properties = new AppProperties();
        properties.getCacheConsistency().setMaxStaleness(Duration.ofMillis(1));
        var runtime =
                new ResourceRuntimeService(
                        new StubResourceService(resource),
                        new ResourceConfigService(cipher),
                        List.of(factory),
                        properties);
        runtime.init();
        var old = runtime.leaseCachedClient(7L, ResourceType.DB, TrackingClient.class);
        resource.setConfig(enabledResource(cipher).getConfig());
        Thread.sleep(25L);
        // Encryption uses a fresh nonce, so a new persisted configuration is a new generation.
        var next = runtime.leaseCachedClient(7L, ResourceType.DB, TrackingClient.class);
        assertFalse(old.getClient() == next.getClient());
        assertFalse(old.getClient().closed);
        old.close();
        assertTrue(old.getClient().closed);
        next.close();
        resource.setEnabled(false);
        Thread.sleep(25L);
        assertThrows(
                BusinessException.class,
                () -> runtime.leaseCachedClient(7L, ResourceType.DB, TrackingClient.class));
        runtime.destroy();
        assertTrue(next.getClient().closed);
    }

    @Test
    void invalidatedGenerationMustRemainAliveUntilItsLastLeaseCloses() {
        ResourceConfigCipher cipher = cipher();
        RecordingFactory factory = new RecordingFactory();
        ResourceRuntimeService runtime = runtime(enabledResource(cipher), cipher, factory);
        var first = runtime.leaseCachedClient(7L, ResourceType.DB, TrackingClient.class);
        var shared = runtime.leaseCachedClient(7L, ResourceType.DB, TrackingClient.class);
        assertSame(first.getClient(), shared.getClient());
        runtime.refresh(7L);
        assertFalse(first.getClient().closed);
        var next = runtime.leaseCachedClient(7L, ResourceType.DB, TrackingClient.class);
        assertFalse(first.getClient() == next.getClient());
        first.close();
        assertFalse(shared.getClient().closed);
        shared.close();
        shared.close();
        assertEquals(1, first.getClient().closeCount);
        runtime.destroy();
        assertFalse(next.getClient().closed);
        next.close();
        assertEquals(1, next.getClient().closeCount);
        assertThrows(
                IllegalStateException.class,
                () -> runtime.leaseCachedClient(7L, ResourceType.DB, TrackingClient.class));
    }

    private ResourceRuntimeService runtime(
            Resource resource, ResourceConfigCipher cipher, RecordingFactory factory) {
        ResourceRuntimeService runtime =
                new ResourceRuntimeService(
                        new StubResourceService(resource),
                        new ResourceConfigService(cipher),
                        List.of(factory));
        runtime.init();
        return runtime;
    }

    private Resource enabledResource(ResourceConfigCipher cipher) {
        DbResourceConfig config = new DbResourceConfig();
        config.setDbType(DbType.MYSQL);
        config.setJdbcUrl("jdbc:mysql://127.0.0.1/demo");
        config.setUsername("demo");
        config.setPassword("database-secret");
        Resource resource = new Resource();
        resource.setId(7L);
        resource.setType(ResourceType.DB);
        resource.setEnabled(true);
        resource.setConfig(cipher.encrypt(JSONUtil.toJsonStr(config)));
        return resource;
    }

    private ResourceConfigCipher cipher() {
        AppProperties properties = new AppProperties();
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) 13);
        properties.getSecurity().getAes().setKey(Base64.getEncoder().encodeToString(key));
        properties.getSecurity().getAes().setKeyId("test");
        return new ResourceConfigCipher(properties);
    }

    private static final class StubResourceService extends ResourceService {
        private final Resource resource;

        private StubResourceService(Resource resource) {
            super(null, null, null, null, event -> {});
            this.resource = resource;
        }

        @Override
        public Resource getResource(Long id) {
            return resource != null && id.equals(resource.getId()) ? resource : null;
        }
    }

    private static final class RecordingFactory implements ResourceClientFactory {
        private int createCount;
        private int verifyCount;
        private boolean failVerification;
        private String passwordSeen;
        private TrackingClient lastClient;

        @Override
        public ResourceType supportType() {
            return ResourceType.DB;
        }

        @Override
        public Object create(Resource resource) {
            createCount++;
            DbResourceConfig config = JSONUtil.toBean(resource.getConfig(), DbResourceConfig.class);
            passwordSeen = config.getPassword();
            lastClient = new TrackingClient();
            return lastClient;
        }

        @Override
        public void verifyConnection(Object client) {
            verifyCount++;
            if (failVerification) {
                throw new IllegalStateException("sensitive-host-detail");
            }
        }
    }

    private static final class TrackingClient implements AutoCloseable {
        private boolean closed;
        private int closeCount;

        @Override
        public void close() {
            closed = true;
            closeCount++;
        }
    }
}
