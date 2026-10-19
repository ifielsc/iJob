// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ifielsc.ijob.common.cluster.ClusterStateStore;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class DatabaseSaTokenDaoTest {
    @Test
    void shouldDelegateStringStateAndTimeoutOperations() {
        RecordingStore store = new RecordingStore();
        DatabaseSaTokenDao dao = new DatabaseSaTokenDao(store);

        dao.set("token:a", "login:1", 60);
        assertEquals("login:1", dao.get("token:a"));
        assertEquals(60, dao.getTimeout("token:a"));

        dao.update("token:a", "login:2");
        dao.updateTimeout("token:a", 120);
        assertEquals("login:2", dao.get("token:a"));
        assertEquals(120, dao.getTimeout("token:a"));
        assertEquals(List.of("token:a"), dao.searchData("token:", "a", 0, 10, true));

        dao.delete("token:a");
        assertNull(dao.get("token:a"));
    }

    private static final class RecordingStore implements ClusterStateStore {
        private final Map<String, String> values = new HashMap<>();
        private final Map<String, Long> timeouts = new HashMap<>();

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public void set(String key, String value, long timeoutSeconds) {
            values.put(key, value);
            timeouts.put(key, timeoutSeconds);
        }

        @Override
        public void update(String key, String value) {
            values.computeIfPresent(key, (ignored, current) -> value);
        }

        @Override
        public void delete(String key) {
            values.remove(key);
            timeouts.remove(key);
        }

        @Override
        public long getTimeout(String key) {
            return timeouts.getOrDefault(key, -2L);
        }

        @Override
        public void updateTimeout(String key, long timeoutSeconds) {
            timeouts.computeIfPresent(key, (ignored, current) -> timeoutSeconds);
        }

        @Override
        public List<String> search(
                String prefix, String keyword, int start, int size, boolean ascending) {
            List<String> keys = new ArrayList<>();
            values.keySet().stream()
                    .filter(key -> key.startsWith(prefix) && key.contains(keyword))
                    .sorted(ascending ? String::compareTo : java.util.Comparator.reverseOrder())
                    .skip(start)
                    .limit(size)
                    .forEach(keys::add);
            return keys;
        }

        @Override
        public String initializeOrRead(String key, String value) {
            return values.computeIfAbsent(key, ignored -> value);
        }
    }
}
