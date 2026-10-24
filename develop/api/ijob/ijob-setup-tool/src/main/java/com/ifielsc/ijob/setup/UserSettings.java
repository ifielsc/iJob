// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.setup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

final class UserSettings {
    private UserSettings() {}

    static List<String[]> catalog() throws IOException {
        try (var input = UserSettings.class.getResourceAsStream("/user-settings.tsv")) {
            if (input == null) throw new IOException("Settings catalog missing");
            try (var reader =
                    new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                return reader.lines()
                        .skip(1)
                        .filter(line -> !line.isBlank())
                        .map(line -> line.split("\t", -1))
                        .toList();
            }
        }
    }

    static void run(String[] args, PrintStream out) throws IOException {
        boolean chinese = "zh".equals(System.getenv("IJOB_LANG"));
        Path home = null;
        for (int i = 1; i < args.length; i += 2) {
            if (i + 1 == args.length) throw new IllegalArgumentException("Missing option value");
            switch (args[i]) {
                case "--lang" -> {
                    if (!List.of("en", "zh").contains(args[i + 1]))
                        throw new IllegalArgumentException("Use --lang en or zh");
                    chinese = "zh".equals(args[i + 1]);
                }
                case "--home" -> home = Path.of(args[i + 1]);
                default -> throw new IllegalArgumentException("Unknown settings option");
            }
        }
        if (args[0].equals("settings")) {
            out.println(SetupMessages.text(chinese, "settings.heading"));
            for (String[] entry : catalog())
                out.println(
                        String.join(" | ", entry[0], entry[1], entry[2], entry[chinese ? 4 : 3]));
            return;
        }
        if (home == null) {
            String configured = System.getenv("IJOB_HOME");
            if (configured == null)
                throw new IllegalArgumentException("Specify --home or IJOB_HOME");
            home = Path.of(configured);
        }
        Properties values =
                new Properties() {
                    @Override
                    public synchronized Object put(Object key, Object value) {
                        if (containsKey(key))
                            throw new IllegalArgumentException("Duplicate configuration key");
                        return super.put(key, value);
                    }
                };
        // Java properties escaping matches the initialization writer; never print actual values.
        try (var input = Files.newInputStream(home.resolve("application.properties"))) {
            values.load(input);
        }
        int invalid = 0;
        for (String[] entry : catalog()) {
            String value = values.getProperty(entry[0]);
            if (value != null && !valid(entry[1], value)) {
                out.println((SetupMessages.text(chinese, "settings.invalid")) + entry[0]);
                invalid++;
            }
        }
        if (invalid > 0) throw new IllegalArgumentException("Configuration validation failed");
        out.println(SetupMessages.text(chinese, "settings.valid"));
    }

    static boolean valid(String type, String value) {
        return switch (type) {
            case "boolean" -> value.equals("true") || value.equals("false");
            case "port", "positive-int" -> {
                try {
                    int number = Integer.parseInt(value);
                    yield number > 0 && (!type.equals("port") || number <= 65535);
                } catch (NumberFormatException error) {
                    yield false;
                }
            }
            case "duration" -> value.matches("[1-9][0-9]*(ms|s|m|h)");
            case "size" -> value.matches("[1-9][0-9]*(KB|MB|GB)");
            case "locale" -> Arrays.asList("en", "en-US", "zh", "zh-CN").contains(value);
            default -> true;
        };
    }
}
