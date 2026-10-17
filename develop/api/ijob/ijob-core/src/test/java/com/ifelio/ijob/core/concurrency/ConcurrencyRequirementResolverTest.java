package com.ifelio.ijob.core.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.infra.entity.concurrency.ConcurrencyResource;
import com.ifelio.ijob.infra.entity.concurrency.PluginConcurrencyPolicy;
import com.ifelio.ijob.infra.entity.concurrency.TaskConcurrencyPolicy;
import com.ifelio.ijob.infra.mapper.concurrency.PluginConcurrencyPolicyMapper;
import com.ifelio.ijob.infra.mapper.concurrency.TaskConcurrencyPolicyMapper;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

class ConcurrencyRequirementResolverTest {

    @Test
    void resolvesPluginTaskAndEverySharedResourceInStableOrder() {
        PluginConcurrencyPolicy plugin = new PluginConcurrencyPolicy();
        plugin.setPluginType(PluginType.SQL);
        plugin.setMaxConcurrency(8);
        TaskConcurrencyPolicy task = new TaskConcurrencyPolicy();
        task.setTaskId(1L);
        task.setTaskMaxConcurrency(1);
        ConcurrencyResource second = resource(20L, 3);
        ConcurrencyResource first = resource(10L, 2);
        PluginConcurrencyPolicyMapper pluginMapper = pluginMapper(plugin);
        TaskConcurrencyPolicyMapper taskMapper = taskMapper(task, List.of(second, first));

        List<ConcurrencyRequirement> result =
                new ConcurrencyRequirementResolver(pluginMapper, taskMapper)
                        .resolve(1L, PluginType.SQL);

        assertEquals(
                List.of(
                        new ConcurrencyRequirement("PLUGIN:SQL", 8),
                        new ConcurrencyRequirement("RESOURCE:10", 2),
                        new ConcurrencyRequirement("RESOURCE:20", 3),
                        new ConcurrencyRequirement("TASK:1", 1)),
                result);
    }

    private PluginConcurrencyPolicyMapper pluginMapper(PluginConcurrencyPolicy value) {
        return new PluginConcurrencyPolicyMapper() {
            @Override
            public List<PluginConcurrencyPolicy> selectAll() {
                return List.of(value);
            }

            @Override
            public PluginConcurrencyPolicy selectByPluginType(PluginType type) {
                return value;
            }

            @Override
            public PluginConcurrencyPolicy selectByPluginTypeForUpdate(PluginType type) {
                return value;
            }

            @Override
            public int insert(PluginConcurrencyPolicy policy) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int update(
                    PluginType type,
                    Integer maximum,
                    Long version,
                    Long newVersion,
                    LocalDateTime time) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int delete(PluginType type, Long version) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private TaskConcurrencyPolicyMapper taskMapper(
            TaskConcurrencyPolicy policy, List<ConcurrencyResource> resources) {
        return new TaskConcurrencyPolicyMapper() {
            @Override
            public TaskConcurrencyPolicy selectByTaskId(Long taskId) {
                return policy;
            }

            @Override
            public int insert(TaskConcurrencyPolicy value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int update(Long taskId, Integer maximum, Long version, LocalDateTime time) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int deleteBindings(Long taskId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int insertBinding(Long taskId, Long resourceId, LocalDateTime time) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<ConcurrencyResource> selectResourcesByTaskId(Long taskId) {
                return resources;
            }
        };
    }

    private ConcurrencyResource resource(long id, int maximum) {
        ConcurrencyResource resource = new ConcurrencyResource();
        resource.setId(id);
        resource.setMaxConcurrency(maximum);
        resource.setEnabled(true);
        return resource;
    }
}
