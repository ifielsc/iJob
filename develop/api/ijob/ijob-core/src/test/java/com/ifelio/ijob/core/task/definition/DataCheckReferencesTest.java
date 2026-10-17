package com.ifelio.ijob.core.task.definition;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.contract.enums.ResourceType;
import com.ifelio.ijob.infra.entity.notify.NotifyConfig;
import com.ifelio.ijob.infra.entity.resource.Resource;
import com.ifelio.ijob.infra.entity.task.TaskDefinition;
import com.ifelio.ijob.infra.mapper.notify.NotifyConfigMapper;
import com.ifelio.ijob.infra.mapper.resource.ResourceMapper;
import com.ifelio.ijob.infra.mapper.task.TaskDefinitionResourceReferenceMapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

class DataCheckReferencesTest {
    @Test
    void monitorReferencesRequireCorrectResourceAndEnabledNotification() {
        var service = service();
        String config =
                "{\"resourceId\":\"1\",\"notify\":{\"enabled\":true,\"notifyConfigId\":\"9223372036854775807\"}}";
        assertThrows(BusinessException.class, () -> service.validateMonitor(config));
        resource.setType(ResourceType.MONITOR_SERVER);
        service.validateMonitor(config);
        assertEquals(List.of("selectIdsForUpdate", "selectById"), calls);
        resource.setEnabled(false);
        assertThrows(BusinessException.class, () -> service.validateMonitor(config));
        resource.setEnabled(true);
        notify.setEnabled(false);
        assertThrows(BusinessException.class, () -> service.validateMonitor(config));
        assertDoesNotThrow(() -> service.validateMonitor(config.replace("true", "false")));
        for (String invalid : new String[] {"1.5", "0", "9223372036854775808"})
            assertThrows(
                    BusinessException.class,
                    () -> service.validateMonitor(config.replace("9223372036854775807", invalid)));
    }

    @Test
    void monitorHistoricalNotificationReferencesPreventDeletion() {
        var service = service();
        var d = new TaskDefinition();
        d.setId(1L);
        d.setPluginType(PluginType.SERVER_MONITOR);
        d.setPluginConfig("{\"notify\":{\"enabled\":true,\"notifyConfigId\":9223372036854775807}}");
        history.add(d);
        var failure =
                assertThrows(
                        BusinessException.class,
                        () -> service.requireUnreferenced(List.of(Long.MAX_VALUE)));
        assertEquals(com.ifelio.ijob.common.response.ResultCode.CONFLICT, failure.getCode());
        assertDoesNotThrow(() -> service.requireUnreferenced(List.of(2L)));
        d.setPluginConfig(d.getPluginConfig().replace("true", "false"));
        assertDoesNotThrow(() -> service.requireUnreferenced(List.of(Long.MAX_VALUE)));
        d.setPluginConfig("broken");
        assertThrows(BusinessException.class, () -> service.requireUnreferenced(List.of(2L)));
    }

    private final Resource resource = new Resource();
    private final NotifyConfig notify = new NotifyConfig();
    private final List<TaskDefinition> history = new ArrayList<>();
    private final List<String> calls = new ArrayList<>();
    private final String config =
            "{\"resourceId\":1,\"handlers\":[{\"type\":\"NOTIFY\",\"params\":{\"notifyConfigId\":9223372036854775807}}]}";

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private DataCheckReferences service() {
        resource.setType(ResourceType.DB);
        resource.setEnabled(true);
        notify.setEnabled(true);
        return new DataCheckReferences(
                proxy(ResourceMapper.class, (p, m, a) -> resource),
                proxy(
                        NotifyConfigMapper.class,
                        (p, m, a) -> {
                            calls.add(m.getName());
                            return m.getName().equals("selectIdsForUpdate") ? a[0] : notify;
                        }),
                proxy(
                        TaskDefinitionResourceReferenceMapper.class,
                        (p, m, a) -> {
                            if (m.getName().equals("selectMaxDefinitionId"))
                                return history.isEmpty() ? null : history.getLast().getId();
                            calls.add("batch");
                            return history.stream()
                                    .filter(d -> d.getId() > (Long) a[0])
                                    .limit(500)
                                    .toList();
                        }));
    }

    @Test
    void requiresEnabledDatabaseAndLocksExactNotificationBeforeReadingIt() {
        var service = service();
        service.validate(config);
        assertEquals(List.of("selectIdsForUpdate", "selectById"), calls);
        resource.setType(ResourceType.SSH);
        assertThrows(BusinessException.class, () -> service.validate(config));
        resource.setType(ResourceType.DB);
        resource.setEnabled(false);
        assertThrows(BusinessException.class, () -> service.validate(config));
        resource.setEnabled(true);
        notify.setEnabled(false);
        assertThrows(BusinessException.class, () -> service.validate(config));
    }

    @Test
    void rejectsFractionalOrOverflowReferencesWithoutTruncation() {
        var service = service();
        for (String invalid :
                new String[] {"9223372036854775808", "9007199254740993.1", "0", "-1", "1.0"})
            assertThrows(
                    BusinessException.class,
                    () -> service.validate(config.replace("9223372036854775807", invalid)));
    }

    @Test
    void deletionScansBeyondFirstPageAndIncludesHistoricalVersions() {
        for (long i = 1; i <= 501; i++) {
            var d = new TaskDefinition();
            d.setId(i);
            d.setPluginType(i == 501 ? PluginType.DATA_CHECK : PluginType.SQL);
            d.setPluginConfig(config);
            history.add(d);
        }
        var service = service();
        assertThrows(
                BusinessException.class,
                () -> service.requireUnreferenced(List.of(Long.MAX_VALUE)));
        assertEquals(2, calls.stream().filter("batch"::equals).count());
        assertDoesNotThrow(() -> service.requireUnreferenced(List.of(2L)));
        history.getLast().setPluginConfig("broken");
        assertThrows(BusinessException.class, () -> service.requireUnreferenced(List.of(2L)));
    }
}
