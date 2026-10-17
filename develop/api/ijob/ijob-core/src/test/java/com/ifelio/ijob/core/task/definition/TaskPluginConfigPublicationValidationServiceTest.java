package com.ifelio.ijob.core.task.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.contract.enums.ResourceType;
import com.ifelio.ijob.contract.resource.ResourceConfigView;
import com.ifelio.ijob.core.resource.ResourceConfigService;
import com.ifelio.ijob.infra.entity.resource.Resource;
import com.ifelio.ijob.infra.mapper.resource.ResourceMapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class TaskPluginConfigPublicationValidationServiceTest {
    @Test
    void publicationShouldLockReferencedResourcesInStableOrder() {
        List<List<Long>> locked = new ArrayList<>();
        ResourceMapper mapper =
                mapper(
                        ids -> {
                            locked.add(List.copyOf(ids));
                            return List.copyOf(ids);
                        });
        TaskPluginConfigPublicationValidationService service = service(mapper, Set.of(9L, 3L));

        service.validate(
                PluginType.SQL, "opaque-config", new TaskPluginConfigPublicationContext(0, null));

        assertEquals(List.of(List.of(3L, 9L)), locked);
    }

    @Test
    void missingResourceShouldUseSafeNotFoundError() {
        ResourceMapper mapper = mapper(ids -> List.of(ids.getFirst()));
        TaskPluginConfigPublicationValidationService service = service(mapper, Set.of(3L, 9L));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.validate(
                                        PluginType.SQL,
                                        "opaque-secret-config",
                                        new TaskPluginConfigPublicationContext(0, null)));

        assertEquals(ResultCode.NOT_FOUND, exception.getCode());
        assertFalse(exception.getMessage().contains("9"));
        assertFalse(exception.getMessage().contains("opaque-secret-config"));
    }

    @Test
    void apiPublicationRequiresEnabledMatchingServiceAndRejectsOAuthOverrides() {
        Resource resource = new Resource();
        resource.setType(ResourceType.SERVICE);
        resource.setEnabled(true);
        ResourceConfigView.Service view = new ResourceConfigView.Service();
        view.setProtocol("HTTP");
        view.setAuthType("NONE");
        ResourceConfigService configs =
                new ResourceConfigService(null) {
                    @Override
                    public ResourceConfigView view(Resource value) {
                        return view;
                    }
                };
        ResourceMapper mapper =
                (ResourceMapper)
                        Proxy.newProxyInstance(
                                ResourceMapper.class.getClassLoader(),
                                new Class<?>[] {ResourceMapper.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "selectIdsForUpdate" -> args[0];
                                            case "selectById" -> resource;
                                            default ->
                                                    throw new UnsupportedOperationException(
                                                            method.getName());
                                        });
        TaskPluginConfigPublicationValidator validator =
                new TaskPluginConfigPublicationValidator() {
                    public Set<PluginType> supportTypes() {
                        return Set.of(PluginType.API_CALL);
                    }

                    public void validate(
                            PluginType type,
                            String config,
                            TaskPluginConfigPublicationContext context) {}

                    public Set<Long> referencedResourceIds(PluginType type, String config) {
                        return Set.of(1L);
                    }
                };
        var service =
                new TaskPluginConfigPublicationValidationService(
                        List.of(validator), mapper, configs, null);
        var context = new TaskPluginConfigPublicationContext(0, null);
        String http = "{\"resourceId\":\"1\",\"headers\":{\"Authorization\":\"hidden\"}}";
        service.validate(PluginType.API_CALL, http, context);
        view.setAuthType("OAUTH2_CLIENT_CREDENTIALS");
        assertThrows(
                BusinessException.class,
                () -> service.validate(PluginType.API_CALL, http, context));
        view.setAuthType("NONE");
        view.setProtocol("WEB_SERVICE");
        assertThrows(
                BusinessException.class,
                () -> service.validate(PluginType.API_CALL, http, context));
        String soap = "{\"resourceId\":\"1\",\"webService\":{}}";
        service.validate(PluginType.API_CALL, soap, context);
        resource.setEnabled(false);
        assertThrows(
                BusinessException.class,
                () -> service.validate(PluginType.API_CALL, soap, context));
        resource.setEnabled(true);
        resource.setType(ResourceType.DB);
        assertThrows(
                BusinessException.class,
                () -> service.validate(PluginType.API_CALL, soap, context));
    }

    private TaskPluginConfigPublicationValidationService service(
            ResourceMapper mapper, Set<Long> references) {
        TaskPluginConfigPublicationValidator validator =
                new TaskPluginConfigPublicationValidator() {
                    @Override
                    public Set<PluginType> supportTypes() {
                        return Set.of(PluginType.SQL);
                    }

                    @Override
                    public void validate(
                            PluginType type,
                            String pluginConfig,
                            TaskPluginConfigPublicationContext context) {
                        // No plugin-specific rule is needed for this test.
                    }

                    @Override
                    public Set<Long> referencedResourceIds(PluginType type, String pluginConfig) {
                        return references;
                    }
                };
        return new TaskPluginConfigPublicationValidationService(
                List.of(validator), mapper, null, null);
    }

    @SuppressWarnings("unchecked")
    private ResourceMapper mapper(java.util.function.Function<List<Long>, List<Long>> lock) {
        return (ResourceMapper)
                Proxy.newProxyInstance(
                        ResourceMapper.class.getClassLoader(),
                        new Class<?>[] {ResourceMapper.class},
                        (proxy, method, arguments) -> {
                            if (method.getName().equals("selectIdsForUpdate")) {
                                return lock.apply((List<Long>) arguments[0]);
                            }
                            throw new UnsupportedOperationException(method.getName());
                        });
    }
}
