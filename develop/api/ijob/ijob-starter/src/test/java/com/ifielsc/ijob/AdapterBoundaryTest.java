package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.core.resource.factory.ResourceClientFactory;
import com.ifielsc.ijob.core.resource.factory.file.client.FileClient;
import com.ifielsc.ijob.core.resource.factory.monitor.client.MonitorClient;
import com.ifielsc.ijob.core.resource.factory.service.client.ServiceClient;
import com.ifielsc.ijob.core.resource.factory.ssh.client.SshClient;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

class AdapterBoundaryTest {
    @Test
    void orchestrationDoesNotAcquireConcreteFactoryOrTransportDependencies() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path source = root.resolve("ijob-core/src/main/java");
        Path adapters = source.resolve("com/ifielsc/ijob/core/resource/factory");
        List<Class<?>> forbidden = new ArrayList<>();
        try (var paths = Files.walk(adapters)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name =
                        source.relativize(file)
                                .toString()
                                .replace('\\', '.')
                                .replace('/', '.')
                                .replaceAll("\\.java$", "");
                Class<?> type = Class.forName(name, false, getClass().getClassLoader());
                if (!type.isInterface()
                        && !Modifier.isAbstract(type.getModifiers())
                        && List.of(
                                        ResourceClientFactory.class,
                                        FileClient.class,
                                        MonitorClient.class,
                                        ServiceClient.class,
                                        SshClient.class)
                                .stream()
                                .anyMatch(boundary -> boundary.isAssignableFrom(type)))
                    forbidden.add(type);
            }
        }
        assertTrue(forbidden.size() >= 10, "Must discover concrete adapter implementations");
        int inspected = 0;
        for (String module : List.of("ijob-core", "ijob-plugin", "ijob-api", "ijob-framework")) {
            Path classes = root.resolve(module + "/target/classes");
            try (var paths = Files.walk(classes)) {
                for (Path file : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                    String relative = classes.relativize(file).toString().replace('\\', '/');
                    if (relative.startsWith("com/ifielsc/ijob/core/resource/factory/")) continue;
                    // JVM constant-pool class names cover method bodies, not just imports/fields.
                    String bytecode =
                            new String(
                                    Files.readAllBytes(file),
                                    java.nio.charset.StandardCharsets.ISO_8859_1);
                    for (Class<?> type : forbidden) {
                        assertFalse(
                                bytecode.contains(type.getName().replace('.', '/')),
                                () ->
                                        "Use a resource interface/lease, not "
                                                + type.getName()
                                                + ": "
                                                + file);
                    }
                    inspected++;
                }
            }
        }
        assertTrue(inspected > 500);
    }

    @Test
    void commonAndContractDoNotReferenceAuthenticationOrPagingEngines() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Pattern forbidden =
                Pattern.compile(
                        "cn\\.dev33|com\\.github\\.pagehelper|org\\.apache\\.ibatis|org\\.mybatis");
        for (String module : List.of("ijob-common", "ijob-contract")) {
            Path source = root.resolve(module + "/src/main/java");
            try (var paths = Files.walk(source)) {
                for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                    assertFalse(forbidden.matcher(Files.readString(file)).find(), file.toString());
                }
            }
        }
    }
}
