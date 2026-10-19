// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.core.execute.task.TaskRunner;
import com.ifielsc.ijob.plugin.apicall.ApiCallTaskRunner;
import com.ifielsc.ijob.plugin.batchetl.BatchEtlTaskRunner;
import com.ifielsc.ijob.plugin.common.config.PluginConfigCodec;
import com.ifielsc.ijob.plugin.datacheck.DataCheckTaskRunner;
import com.ifielsc.ijob.plugin.etl.EtlTaskRunner;
import com.ifielsc.ijob.plugin.event.EventTaskRunner;
import com.ifielsc.ijob.plugin.filetransfer.FileTransferTaskRunner;
import com.ifielsc.ijob.plugin.localjar.LocalJarTaskRunner;
import com.ifielsc.ijob.plugin.push.PushTaskRunner;
import com.ifielsc.ijob.plugin.push.compress.CompressionRegistry;
import com.ifielsc.ijob.plugin.push.compress.CompressionService;
import com.ifielsc.ijob.plugin.push.publisher.SerializedPayload;
import com.ifielsc.ijob.plugin.push.serializer.MessageSerializer;
import com.ifielsc.ijob.plugin.push.serializer.MessageSerializerRegistry;
import com.ifielsc.ijob.plugin.script.ScriptTaskRunner;
import com.ifielsc.ijob.plugin.servermonitor.ServerMonitorTaskRunner;
import com.ifielsc.ijob.plugin.sql.SqlTaskRunner;

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
