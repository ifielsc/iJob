package com.ifielsc.ijob.core.task.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.contract.enums.PluginType;

import org.junit.jupiter.api.Test;

import java.util.List;

class TaskPluginConfigSnapshotServiceTest {
    @Test
    void shouldPreserveConfigWhenTypeHasNoSnapshotter() {
        TaskPluginConfigSnapshotService service = new TaskPluginConfigSnapshotService(List.of());

        assertEquals(
                "{\"sql\":\"select 1\"}",
                service.snapshot(PluginType.SQL, "{\"sql\":\"select 1\"}"));
    }

    @Test
    void shouldDelegateToMatchingSnapshotter() {
        TaskPluginConfigSnapshotService service =
                new TaskPluginConfigSnapshotService(
                        List.of(snapshotter(PluginType.LOCAL_JAR, "snapshot")));

        assertEquals("snapshot", service.snapshot(PluginType.LOCAL_JAR, "draft"));
    }

    @Test
    void shouldRejectDuplicateSnapshotters() {
        TaskPluginConfigSnapshotter first = snapshotter(PluginType.LOCAL_JAR, "first");
        TaskPluginConfigSnapshotter second = snapshotter(PluginType.LOCAL_JAR, "second");

        assertThrows(
                IllegalStateException.class,
                () -> new TaskPluginConfigSnapshotService(List.of(first, second)));
    }

    private TaskPluginConfigSnapshotter snapshotter(PluginType type, String result) {
        return new TaskPluginConfigSnapshotter() {
            @Override
            public PluginType supportType() {
                return type;
            }

            @Override
            public String snapshot(String pluginConfig) {
                return result;
            }
        };
    }
}
