package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.response.MessageDefinition;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Cross-layer identity/argument gate; output is a generated build artifact, not a new runtime
 * registry.
 */
class MessageCatalogContractTest {
    private static final Pattern ARGUMENT = Pattern.compile("\\{(\\d+)(?:[,}])");

    @Test
    void stableCatalogAndBothTranslationsHaveConsistentIdentitiesAndArguments() throws Exception {
        Properties english = bundle("messages.properties");
        Properties chinese = bundle("messages_zh.properties");
        assertEquals(
                english.stringPropertyNames(),
                chinese.stringPropertyNames(),
                "Translation keys drifted");
        var messages = new TreeMap<String, Map<String, Object>>();
        var codeToKey = new TreeMap<String, String>();
        var keyToCodes = new TreeMap<String, java.util.Set<String>>();
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(MessageDefinition.class));
        for (var bean : scanner.findCandidateComponents("com.ifielsc.ijob")) {
            Class<?> type = Class.forName(bean.getBeanClassName());
            if (!type.isEnum()) continue;
            for (Object constant : type.getEnumConstants()) {
                MessageDefinition definition = (MessageDefinition) constant;
                String code = definition.errorCode();
                String key = definition.messageKey();
                String oldKey = codeToKey.putIfAbsent(code, key);
                keyToCodes.computeIfAbsent(key, ignored -> new TreeSet<>()).add(code);
                if (oldKey != null) assertEquals(oldKey, key, "Code collision: " + code);

                assertNotNull(english.getProperty(key), key);
                assertNotNull(chinese.getProperty(key), key);
                // Some errors intentionally have a parameter-free safe fallback.
                assertTrue(
                        arguments(english.getProperty(key))
                                .containsAll(arguments(definition.defaultMessage())),
                        key);
                String identity = type.getName() + "." + ((Enum<?>) constant).name();
                var item =
                        messages.computeIfAbsent(
                                key + ":" + code,
                                ignored -> {
                                    var value = new TreeMap<String, Object>();
                                    value.put("errorCode", code);
                                    value.put("messageKey", key);
                                    value.put("arguments", arguments(english.getProperty(key)));
                                    value.put("en", english.getProperty(key));
                                    value.put("zh", chinese.getProperty(key));
                                    value.put("declarations", new ArrayList<String>());
                                    return value;
                                });
                @SuppressWarnings("unchecked")
                var declarations = (List<String>) item.get("declarations");
                declarations.add(identity);
            }
        }
        // Two published codes intentionally share this existing translation. Preserve both
        // identities; any additional collision must be reviewed rather than silently accepted.
        var knownSharedKeys =
                Map.of(
                        "error.detail.request-body-is-required",
                        java.util.Set.of("IJOB-DETAIL-0127", "IJOB-DETAIL-0196"));
        var actualSharedKeys = new TreeMap<String, java.util.Set<String>>();
        keyToCodes.forEach(
                (key, codes) -> {
                    if (codes.size() > 1) actualSharedKeys.put(key, codes);
                });
        assertEquals(knownSharedKeys, actualSharedKeys, "Unreviewed shared translation key");
        assertTrue(messages.size() > 300, "Catalog scanner missed domain declarations");
        for (String key : english.stringPropertyNames()) {
            assertFalse(english.getProperty(key).isBlank(), key);
            assertFalse(chinese.getProperty(key).isBlank(), key);
            assertEquals(
                    arguments(english.getProperty(key)), arguments(chinese.getProperty(key)), key);
        }
        for (var item : messages.values()) {
            @SuppressWarnings("unchecked")
            var declarations = (List<String>) item.get("declarations");
            declarations.sort(String::compareTo);
        }
        Path output = Path.of("target", "message-catalog.json");
        Files.createDirectories(output.getParent());
        new ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValue(output.toFile(), messages.values());
    }

    private static List<Integer> arguments(String value) {
        var result = new TreeSet<Integer>();
        var matcher = ARGUMENT.matcher(value);
        while (matcher.find()) result.add(Integer.parseInt(matcher.group(1)));
        return List.copyOf(result);
    }

    private static Properties bundle(String path) throws Exception {
        Properties values = new Properties();
        try (var stream =
                MessageCatalogContractTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                values.load(reader);
            }
        }
        return values;
    }
}
