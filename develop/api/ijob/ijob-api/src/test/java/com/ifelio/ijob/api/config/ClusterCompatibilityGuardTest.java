package com.ifelio.ijob.api.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.dev33.satoken.config.SaTokenConfig;

import com.ifelio.ijob.common.cluster.ClusterStateStore;
import com.ifelio.ijob.common.config.AppProperties;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ClusterCompatibilityGuardTest {
    @Test
    void equalConfigurationShouldJoinWhileDifferentConfigurationIsRejected() {
        MemoryStore store = new MemoryStore();

        assertDoesNotThrow(() -> guard(store, properties("same-hash")).afterPropertiesSet());
        assertDoesNotThrow(() -> guard(store, properties("same-hash")).afterPropertiesSet());
        assertThrows(
                IllegalStateException.class,
                () -> guard(store, properties("different-hash")).afterPropertiesSet());
    }

    private ClusterCompatibilityGuard guard(MemoryStore store, AppProperties properties) {
        SaTokenConfig config = new SaTokenConfig();
        config.setTokenName("ijob-token");
        config.setTimeout(1800);
        config.setActiveTimeout(300);
        config.setIsConcurrent(false);
        config.setIsShare(false);
        config.setTokenStyle("uuid");
        config.setIsReadHeader(true);
        config.setIsReadCookie(false);
        return new ClusterCompatibilityGuard(store, properties, config);
    }

    private AppProperties properties(String passwordHash) {
        AppProperties properties = new AppProperties();
        properties.getAuth().setUsername("admin");
        properties.getAuth().setPasswordHash(passwordHash);
        properties.getSecurity().getAes().setKeyId("cluster-key");
        properties.getSecurity().getAes().setKey("secret-key-material");
        return properties;
    }

    private static final class MemoryStore implements ClusterStateStore {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public void set(String key, String value, long timeoutSeconds) {
            values.put(key, value);
        }

        @Override
        public void update(String key, String value) {
            values.put(key, value);
        }

        @Override
        public void delete(String key) {
            values.remove(key);
        }

        @Override
        public long getTimeout(String key) {
            return values.containsKey(key) ? -1 : -2;
        }

        @Override
        public void updateTimeout(String key, long timeoutSeconds) {}

        @Override
        public List<String> search(
                String prefix, String keyword, int start, int size, boolean ascending) {
            return List.of();
        }

        @Override
        public String initializeOrRead(String key, String value) {
            return values.computeIfAbsent(key, ignored -> value);
        }
    }
}
