package com.ifielsc.ijob.migration;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

record ConnectionSettings(String url, String user, String password, String schema, String driver) {

    static ConnectionSettings load(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        String url = required(properties, "jdbc.url");
        String user = environment(properties, "jdbc.userEnv", false);
        String password = environment(properties, "jdbc.passwordEnv", true);
        String schema = trimToNull(properties.getProperty("jdbc.schema"));
        String driver = trimToNull(properties.getProperty("jdbc.driver"));
        return new ConnectionSettings(url, user, password, schema, driver);
    }

    private static String environment(Properties properties, String key, boolean allowEmpty) {
        String variable = required(properties, key);
        String value = System.getenv(variable);
        if (value == null || (!allowEmpty && value.isBlank())) {
            throw new IllegalArgumentException(
                    "Environment variable referenced by " + key + " is not set");
        }
        return value;
    }

    private static String required(Properties properties, String key) {
        String value = trimToNull(properties.getProperty(key));
        if (value == null) {
            throw new IllegalArgumentException("Missing required property: " + key);
        }
        return value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    ConnectionSettings {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(password, "password");
    }
}
