package com.ifelio.ijob.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.core.execute.task.TaskRunner;
import com.ifelio.ijob.plugin.apicall.ApiCallTaskRunner;
import com.ifelio.ijob.plugin.batchetl.BatchEtlTaskRunner;
import com.ifelio.ijob.plugin.common.config.PluginConfigCodec;
import com.ifelio.ijob.plugin.datacheck.DataCheckTaskRunner;
import com.ifelio.ijob.plugin.etl.EtlTaskRunner;
import com.ifelio.ijob.plugin.event.EventTaskRunner;
import com.ifelio.ijob.plugin.filetransfer.FileTransferTaskRunner;
import com.ifelio.ijob.plugin.localjar.LocalJarTaskRunner;
import com.ifelio.ijob.plugin.push.PushTaskRunner;
import com.ifelio.ijob.plugin.push.compress.CompressionRegistry;
import com.ifelio.ijob.plugin.push.compress.CompressionService;
import com.ifelio.ijob.plugin.push.publisher.SerializedPayload;
import com.ifelio.ijob.plugin.push.serializer.MessageSerializer;
import com.ifelio.ijob.plugin.push.serializer.MessageSerializerRegistry;
import com.ifelio.ijob.plugin.script.ScriptTaskRunner;
import com.ifelio.ijob.plugin.servermonitor.ServerMonitorTaskRunner;
import com.ifelio.ijob.plugin.sql.SqlTaskRunner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

class PluginInfrastructureConsistencyTest {
    @Test
    void allTaskRunnersShouldDeclareAnEnableSwitch() {
        List<Class<? extends TaskRunner>> runners =
                List.of(
                        EventTaskRunner.class,
                        ScriptTaskRunner.class,
                        SqlTaskRunner.class,
                        ApiCallTaskRunner.class,
                        FileTransferTaskRunner.class,
                        ServerMonitorTaskRunner.class,
                        PushTaskRunner.class,
                        DataCheckTaskRunner.class,
                        EtlTaskRunner.class,
                        BatchEtlTaskRunner.class,
                        LocalJarTaskRunner.class);

        for (Class<? extends TaskRunner> runner : runners) {
            assertNotNull(
                    runner.getAnnotation(ConditionalOnProperty.class), runner.getSimpleName());
        }
    }

    @Test
    void availabilityDefaultsShouldKeepLocalJarOptIn() {
        PluginAvailabilityProperties properties = new PluginAvailabilityProperties();

        assertTrue(properties.getScript().isEnabled());
        assertTrue(properties.getDataCheck().isEnabled());
        assertTrue(properties.getEtl().isEnabled());
        assertTrue(properties.getBatchEtl().isEnabled());
        assertFalse(properties.getLocalJar().isEnabled());
    }

    @Test
    void codecShouldExposeStableInvalidMessage() {
        PluginConfigCodec codec = new PluginConfigCodec(new ObjectMapper());

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> codec.read("{", Object.class, "Example"));

        assertEquals("Example config is invalid", exception.getMessage());
    }

    @Test
    void duplicateCompressionNameShouldFailFast() {
        CompressionService first = compression("gzip");
        CompressionService second = compression("gzip");

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> new CompressionRegistry(List.of(first, second)));

        assertEquals("duplicate compression service: gzip", exception.getMessage());
    }

    @Test
    void duplicateSerializerNameShouldFailFast() {
        MessageSerializer first = serializer("json");
        MessageSerializer second = serializer("json");

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> new MessageSerializerRegistry(List.of(first, second)));

        assertEquals("duplicate message serializer: json", exception.getMessage());
    }

    private CompressionService compression(String name) {
        return new CompressionService() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean support(SerializedPayload payload) {
                return true;
            }

            @Override
            public SerializedPayload compress(SerializedPayload payload) {
                return payload;
            }
        };
    }

    private MessageSerializer serializer(String name) {
        return new MessageSerializer() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public SerializedPayload serialize(Object object) {
                return null;
            }
        };
    }
}
