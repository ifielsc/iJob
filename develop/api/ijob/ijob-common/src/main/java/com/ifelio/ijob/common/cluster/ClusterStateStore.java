package com.ifelio.ijob.common.cluster;

import java.util.List;

public interface ClusterStateStore {
    String get(String key);

    void set(String key, String value, long timeoutSeconds);

    void update(String key, String value);

    void delete(String key);

    long getTimeout(String key);

    void updateTimeout(String key, long timeoutSeconds);

    List<String> search(String prefix, String keyword, int start, int size, boolean ascending);

    String initializeOrRead(String key, String value);
}
