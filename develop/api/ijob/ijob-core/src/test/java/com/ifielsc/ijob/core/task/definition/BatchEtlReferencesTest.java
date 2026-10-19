package com.ifielsc.ijob.core.task.definition;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.infra.entity.resource.Resource;
import com.ifielsc.ijob.infra.mapper.resource.ResourceMapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

class BatchEtlReferencesTest {
    @Test
    void bothReferencesMustBeEnabledDatabases() {
        Resource source = resource();
        Resource target = resource();
        ResourceMapper mapper =
                (ResourceMapper)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {ResourceMapper.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "selectIdsForUpdate" -> args[0];
                                            case "selectById" ->
                                                    args[0].equals(1L) ? source : target;
                                            default -> throw new UnsupportedOperationException();
                                        });
        TaskPluginConfigPublicationValidator validator =
                new TaskPluginConfigPublicationValidator() {
                    public Set<PluginType> supportTypes() {
                        return Set.of(PluginType.BATCH_ETL);
                    }

                    public void validate(
                            PluginType type,
                            String json,
                            TaskPluginConfigPublicationContext context) {}

                    public Set<Long> referencedResourceIds(PluginType type, String json) {
                        return Set.of(1L, 2L);
                    }
                };
        var service =
                new TaskPluginConfigPublicationValidationService(
                        List.of(validator), mapper, null, null);
        var context = new TaskPluginConfigPublicationContext(0, null);
        String json = "{\"sourceResourceId\":\"1\",\"targetResourceId\":\"2\"}";
        service.validate(PluginType.BATCH_ETL, json, context);
        for (Resource resource : List.of(source, target)) {
            resource.setEnabled(false);
            assertThrows(
                    BusinessException.class,
                    () -> service.validate(PluginType.BATCH_ETL, json, context));
            resource.setEnabled(true);
            resource.setType(ResourceType.FILE);
            assertThrows(
                    BusinessException.class,
                    () -> service.validate(PluginType.BATCH_ETL, json, context));
            resource.setType(ResourceType.DB);
        }
    }

    private Resource resource() {
        var resource = new Resource();
        resource.setType(ResourceType.DB);
        resource.setEnabled(true);
        return resource;
    }
}
