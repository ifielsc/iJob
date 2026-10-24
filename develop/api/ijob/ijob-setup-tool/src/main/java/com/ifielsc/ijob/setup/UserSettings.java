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
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

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

    private static boolean validList(String type, String value) {
        if (value.isBlank()) return !type.equals("roles");
        Set<String> unique = new HashSet<>();
        for (String part : value.split(",", -1)) {
            String item = part.trim();
            if (type.equals("groups")) {
                item = item.toLowerCase(Locale.ROOT);
                if (!item.matches("[a-z0-9][a-z0-9._-]{0,63}")) return false;
            } else if (type.equals("ids")) {
                try {
                    long id = Long.parseLong(item);
                    if (id <= 0) return false;
                    item = Long.toString(id);
                } catch (NumberFormatException error) {
                    return false;
                }
            } else if (!List.of("API", "SCHEDULER", "WORKER", "MAINTENANCE").contains(item)) {
                return false;
            }
            unique.add(item);
        }
        return unique.size() <= (type.equals("groups") ? 16 : 100);
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
            case "session-seconds" -> {
                try {
                    long seconds = Long.parseLong(value);
                    yield seconds == -1 || seconds > 0;
                } catch (NumberFormatException error) {
                    yield false;
                }
            }
            case "nonnegative-duration" -> value.matches("(0|[1-9][0-9]*)(ms|s|m|h|d)");
            case "duration" -> value.matches("[1-9][0-9]*(ms|s|m|h|d)");
            case "size" -> value.matches("[1-9][0-9]*(KB|MB|GB)");
            case "locale" -> Arrays.asList("en", "en-US", "zh", "zh-CN").contains(value);
            case "notification-locale" -> List.of("en", "zh").contains(value);
            case "misfire" -> List.of("SKIP", "FIRE_ONCE", "CATCH_UP_BOUNDED").contains(value);
            case "zone" -> {
                try {
                    ZoneId.of(value);
                    yield true;
                } catch (DateTimeException error) {
                    yield false;
                }
            }
            case "groups", "ids", "roles" -> validList(type, value);
            case "text", "secret" -> true;
            default -> false;
        };
    }
}
