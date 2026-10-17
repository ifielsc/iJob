package com.ifelio.ijob.core.resource;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.ResourceType;
import com.ifelio.ijob.contract.resource.ResourceConnectionTestDTO;
import com.ifelio.ijob.core.error.CoreMessage;
import com.ifelio.ijob.core.resource.factory.ResourceClientFactory;
import com.ifelio.ijob.infra.entity.resource.Resource;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ResourceRuntimeService {
    private static final Set<ResourceType> CACHEABLE_TYPES =
            Set.of(ResourceType.DB, ResourceType.SERVICE);

    private final ResourceService resourceService;
    private final ResourceConfigService configService;
    private final List<ResourceClientFactory> factories;
    private final java.time.Duration maxStaleness;

    private LoadingCache<Long, CachedClient> cache;
    private Map<ResourceType, ResourceClientFactory> factoryMap;
    private volatile boolean closed;

    public ResourceRuntimeService(
            ResourceService resourceService,
            ResourceConfigService configService,
            List<ResourceClientFactory> factories) {
        this(resourceService, configService, factories, java.time.Duration.ofSeconds(5));
    }

    @Autowired
    public ResourceRuntimeService(
            ResourceService resourceService,
            ResourceConfigService configService,
            List<ResourceClientFactory> factories,
            AppProperties appProperties) {
        this(
                resourceService,
                configService,
                factories,
                appProperties.getCacheConsistency().getMaxStaleness());
    }

    private ResourceRuntimeService(
            ResourceService resourceService,
            ResourceConfigService configService,
            List<ResourceClientFactory> factories,
            java.time.Duration maxStaleness) {
        this.resourceService = resourceService;
        this.configService = configService;
        this.factories = factories;
        this.maxStaleness = maxStaleness;
    }

    @PostConstruct
    public void init() {
        factoryMap =
                factories.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        ResourceClientFactory::supportType, Function.identity()));
        cache =
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterAccess(java.time.Duration.ofMinutes(30))
                        .executor(Runnable::run)
                        .removalListener(
                                (Long id,
                                        CachedClient cached,
                                        com.github.benmanes.caffeine.cache.RemovalCause cause) ->
                                        closeCached(id, cached))
                        .build(this::loadClient);
    }

    public String endpointIdentity(Long resourceId) {
        Resource resource = requireEnabledResource(resourceId);
        var config = cn.hutool.json.JSONUtil.parseObj(configService.decryptForRuntime(resource));
        var identity = new java.util.TreeMap<String, Object>();
        for (String key :
                java.util.List.of(
                        "bootstrapServers",
                        "host",
                        "port",
                        "virtualHost",
                        "jdbcUrl",
                        "schema",
                        "dbType")) if (config.containsKey(key)) identity.put(key, config.get(key));
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            cn.hutool.json.JSONUtil.toJsonStr(identity)
                                                    .getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private CachedClient loadClient(Long resourceId) {
        Resource resource = requireEnabledResource(resourceId);
        return createClient(resource);
    }

    private CachedClient createClient(Resource resource) {
        Long resourceId = resource.getId();
        ResourceClientFactory factory = requireFactory(resource.getType());
        try {
            Resource runtimeResource = runtimeCopy(resource);
            Object client = factory.create(runtimeResource);
            if (client == null) {
                throw new IllegalStateException("factory returned null");
            }
            return new CachedClient(resource.getType(), factory, client, resource.getConfig());
        } catch (Exception exception) {
            if (exception instanceof BusinessException business
                    && CoreMessage.SSH_HOST_KEY_MISMATCH
                            .errorCode()
                            .equals(business.getErrorCode())) throw business;
            log.warn(
                    "resource client creation failed, id={}, type={}",
                    resourceId,
                    resource.getType(),
                    com.ifelio.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
            throw new BusinessException(
                    ResultCode.SERVICE_UNAVAILABLE, CoreMessage.RESOURCE_CLIENT_CREATION_FAILED);
        }
    }

    public <T> ResourceClientLease<T> leaseCachedClient(
            Long resourceId, ResourceType expectedType, Class<T> clientType) {
        if (expectedType == null || clientType == null) {
            throw new BusinessException(
                    ResultCode.PARAM_ERROR, CoreMessage.RESOURCE_TYPE_AND_CLIENT_TYPE_ARE_REQUIRED);
        }
        if (!CACHEABLE_TYPES.contains(expectedType)) {
            throw new BusinessException(
                    ResultCode.PARAM_ERROR,
                    CoreMessage.THIS_RESOURCE_TYPE_REQUIRES_AN_EXCLUSIVE_CLIENT_LEASE);
        }
        Long id = requirePositiveId(resourceId);
        while (true) {
            if (closed) throw new IllegalStateException("Resource runtime is closed");
            // Check freshness synchronously, but keep an unchanged, usable pool alive.
            CachedClient cached =
                    cache.asMap()
                            .compute(
                                    id,
                                    (key, existing) -> {
                                        if (existing == null) return loadClient(key);
                                        long now = System.nanoTime();
                                        if (now - existing.checkedAt < maxStaleness.toNanos())
                                            return existing;
                                        Resource resource = requireEnabledResource(key);
                                        if (resource.getType() == existing.type
                                                && java.util.Objects.equals(
                                                        resource.getConfig(), existing.config)) {
                                            existing.checkedAt = now;
                                            return existing;
                                        }
                                        return createClient(resource);
                                    });
            if (closed) {
                cached.retire();
                cache.invalidate(id);
                throw new IllegalStateException("Resource runtime is closed");
            }
            if (cached.type() != expectedType) {
                throw BusinessException.localized(ResultCode.NOT_FOUND, "resource type mismatch");
            }
            if (!clientType.isInstance(cached.client())) {
                throw new BusinessException(
                        ResultCode.SERVICE_UNAVAILABLE,
                        CoreMessage.RESOURCE_CLIENT_TYPE_IS_UNAVAILABLE);
            }
            if (!cached.retain()) continue;
            if (closed) {
                cached.retire();
                cached.release();
                cache.invalidate(id);
                throw new IllegalStateException("Resource runtime is closed");
            }
            return new ResourceClientLease<>(clientType.cast(cached.client()), cached::release);
        }
    }

    public <T> ResourceClientLease<T> leaseClient(
            Long resourceId, ResourceType expectedType, Class<T> clientType) {
        if (expectedType == null || clientType == null) {
            throw new BusinessException(
                    ResultCode.PARAM_ERROR, CoreMessage.RESOURCE_TYPE_AND_CLIENT_TYPE_ARE_REQUIRED);
        }
        Resource resource = requireEnabledResource(resourceId);
        if (resource.getType() != expectedType) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "resource type mismatch");
        }
        ResourceClientFactory factory = requireFactory(resource.getType());
        Object client = null;
        try {
            client = factory.create(runtimeCopy(resource));
            if (!clientType.isInstance(client)) {
                throw new IllegalStateException("factory returned incompatible client");
            }
            T typedClient = clientType.cast(client);
            Object leasedClient = client;
            return new ResourceClientLease<>(typedClient, () -> factory.close(leasedClient));
        } catch (Exception exception) {
            closeCreatedClient(factory, client);
            if (exception instanceof BusinessException business
                    && CoreMessage.SSH_HOST_KEY_MISMATCH
                            .errorCode()
                            .equals(business.getErrorCode())) throw business;
            log.warn(
                    "exclusive resource client creation failed, id={}, type={}",
                    resourceId,
                    resource.getType(),
                    com.ifelio.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
            throw new BusinessException(
                    ResultCode.SERVICE_UNAVAILABLE, CoreMessage.RESOURCE_CLIENT_CREATION_FAILED);
        }
    }

    public ResourceClientLease<com.ifelio.ijob.core.resource.mq.MessageSourceSession>
            leaseMessageSource(
                    Long id,
                    ResourceType type,
                    com.ifelio.ijob.core.resource.mq.MessageSourceOptions options) {
        Resource resource = requireEnabledResource(id);
        if (resource.getType() != type
                || (type != ResourceType.KAFKA && type != ResourceType.RABBITMQ))
            throw BusinessException.localized(ResultCode.CONFLICT, "resource type mismatch");
        var session = requireFactory(type).createMessageSource(runtimeCopy(resource), options);
        return new ResourceClientLease<>(session, session::close);
    }

    public ResourceConnectionTestDTO testConnection(Long resourceId) {
        Resource resource = requireEnabledResource(resourceId);
        ResourceClientFactory factory = requireFactory(resource.getType());
        try {
            factory.testConnection(runtimeCopy(resource));
            return new ResourceConnectionTestDTO(resource.getId(), resource.getType(), true);
        } catch (Exception exception) {
            if (exception instanceof BusinessException business
                    && CoreMessage.SSH_HOST_KEY_MISMATCH
                            .errorCode()
                            .equals(business.getErrorCode())) throw business;
            log.warn(
                    "resource connection test failed, id={}, type={}",
                    resourceId,
                    resource.getType(),
                    com.ifelio.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
            throw new BusinessException(
                    ResultCode.CONFLICT, CoreMessage.RESOURCE_CONNECTION_TEST_FAILED);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onResourceChanged(ResourceCacheInvalidationEvent event) {
        if (event != null && event.resourceId() != null) {
            refresh(event.resourceId());
        }
    }

    public void refresh(Long resourceId) {
        if (cache != null && resourceId != null) {
            cache.invalidate(resourceId);
            cache.cleanUp();
        }
    }

    public void remove(Long resourceId) {
        refresh(resourceId);
    }

    @PreDestroy
    public void destroy() {
        closed = true;
        if (cache != null) {
            cache.invalidateAll();
            cache.cleanUp();
        }
    }

    private Resource requireEnabledResource(Long resourceId) {
        Long id = requirePositiveId(resourceId);
        Resource resource = resourceService.getResource(id);
        if (resource == null) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "resource not found");
        }
        if (!Boolean.TRUE.equals(resource.getEnabled())) {
            throw new BusinessException(ResultCode.CONFLICT, CoreMessage.RESOURCE_IS_DISABLED);
        }
        if (resource.getType() == null) {
            throw new BusinessException(
                    ResultCode.CONFLICT, CoreMessage.RESOURCE_TYPE_IS_UNAVAILABLE);
        }
        return resource;
    }

    private ResourceClientFactory requireFactory(ResourceType type) {
        ResourceClientFactory factory = factoryMap.get(type);
        if (factory == null) {
            throw new BusinessException(
                    ResultCode.SERVICE_UNAVAILABLE,
                    CoreMessage.RESOURCE_TYPE_IS_NOT_SUPPORTED_FOR_EXECUTION);
        }
        return factory;
    }

    private Resource runtimeCopy(Resource resource) {
        Resource copy = new Resource();
        copy.setId(resource.getId());
        copy.setType(resource.getType());
        copy.setEnabled(resource.getEnabled());
        copy.setConfig(configService.decryptForRuntime(resource));
        return copy;
    }

    private Long requirePositiveId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(
                    ResultCode.PARAM_ERROR, CoreMessage.RESOURCE_ID_MUST_BE_GREATER_THAN_ZERO);
        }
        return id;
    }

    private void closeCached(Long id, CachedClient cached) {
        if (cached == null) {
            return;
        }
        try {
            cached.retire();
        } catch (Exception exception) {
            log.warn(
                    "resource client close failed, id={}, type={}",
                    id,
                    cached.type(),
                    com.ifelio.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
        }
    }

    private void closeCreatedClient(ResourceClientFactory factory, Object client) {
        if (client == null) {
            return;
        }
        try {
            factory.close(client);
        } catch (Exception exception) {
            log.warn(
                    "resource client cleanup after creation failure failed",
                    com.ifelio.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
        }
    }

    private static final class CachedClient {
        private final ResourceType type;
        private final ResourceClientFactory factory;
        private final Object client;
        private final String config;
        private volatile long checkedAt = System.nanoTime();
        private int borrowers;
        private boolean retired;
        private boolean disposed;

        CachedClient(
                ResourceType type, ResourceClientFactory factory, Object client, String config) {
            this.type = type;
            this.factory = factory;
            this.client = client;
            this.config = config;
        }

        ResourceType type() {
            return type;
        }

        Object client() {
            return client;
        }

        synchronized boolean retain() {
            if (retired) return false;
            borrowers++;
            return true;
        }

        void release() {
            boolean close;
            synchronized (this) {
                if (borrowers <= 0) throw new IllegalStateException("Unbalanced resource lease");
                borrowers--;
                close = retireIfUnused();
            }
            if (close) dispose();
        }

        void retire() {
            boolean close;
            synchronized (this) {
                retired = true;
                close = retireIfUnused();
            }
            if (close) dispose();
        }

        private boolean retireIfUnused() {
            if (!retired || borrowers != 0 || disposed) return false;
            disposed = true;
            return true;
        }

        private void dispose() {
            try {
                factory.close(client);
            } catch (Exception exception) {
                log.warn(
                        "Retired resource close failed, type={}",
                        type,
                        com.ifelio.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
            }
        }
    }
}
