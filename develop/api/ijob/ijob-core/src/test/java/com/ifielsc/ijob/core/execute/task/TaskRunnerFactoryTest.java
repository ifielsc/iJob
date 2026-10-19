package com.ifielsc.ijob.core.execute.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;

import org.junit.jupiter.api.Test;

import java.util.List;

class TaskRunnerFactoryTest {
    @Test
    void shouldRoutePluginTypeToRegisteredRunner() {
        TaskRunner eventRunner = runner(PluginType.EVENT);
        TaskRunnerFactory factory = new TaskRunnerFactory(List.of(eventRunner));

        assertSame(eventRunner, factory.getRunner(PluginType.EVENT));
    }

    @Test
    void shouldRejectUnsupportedPluginType() {
        TaskRunnerFactory factory = new TaskRunnerFactory(List.of());

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> factory.getRunner(PluginType.SCRIPT));

        assertEquals("unsupported plugin type: SCRIPT", exception.getMessage());
    }

    @Test
    void shouldRejectDuplicatePluginType() {
        TaskRunner first = runner(PluginType.SQL);
        TaskRunner second = runner(PluginType.SQL);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> new TaskRunnerFactory(List.of(first, second)));

        assertEquals("duplicate task runner type: SQL", exception.getMessage());
    }

    private TaskRunner runner(PluginType pluginType) {
        return new TaskRunner() {
            @Override
            public PluginType supportType() {
                return pluginType;
            }

            @Override
            public TaskResult run(TaskExecutionSpec spec, TaskContext context) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
