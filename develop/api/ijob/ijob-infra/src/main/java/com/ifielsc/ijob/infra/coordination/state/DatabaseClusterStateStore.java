package com.ifielsc.ijob.infra.coordination.state;

import com.ifielsc.ijob.common.cluster.ClusterStateStore;
import com.ifielsc.ijob.infra.mapper.coordination.ClusterStateMapper;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatabaseClusterStateStore implements ClusterStateStore {
    public static final long NEVER_EXPIRE = -1;
    public static final long NOT_VALUE_EXPIRE = -2;

    private final ClusterStateMapper mapper;

    public DatabaseClusterStateStore(ClusterStateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String get(String key) {
        return mapper.selectValue(key);
    }

    @Override
    public void set(String key, String value, long timeoutSeconds) {
        mapper.upsert(key, value, timeoutSeconds);
    }

    @Override
    public void update(String key, String value) {
        mapper.updateValue(key, value);
    }

    @Override
    public void delete(String key) {
        mapper.delete(key);
    }

    @Override
    public long getTimeout(String key) {
        Long timeout = mapper.selectTimeoutSeconds(key);
        return timeout == null ? NOT_VALUE_EXPIRE : timeout;
    }

    @Override
    public void updateTimeout(String key, long timeoutSeconds) {
        mapper.updateTimeout(key, timeoutSeconds);
    }

    @Override
    public List<String> search(
            String prefix, String keyword, int start, int size, boolean ascending) {
        String resolvedPrefix = prefix == null ? "" : prefix;
        String resolvedKeyword = keyword == null ? "" : keyword;
        int resolvedSize = size == -1 ? Integer.MAX_VALUE : Math.max(size, 0);
        return mapper.searchKeys(
                resolvedPrefix, resolvedKeyword, Math.max(start, 0), resolvedSize, ascending);
    }

    @Override
    public String initializeOrRead(String key, String value) {
        mapper.insertIfAbsent(key, value);
        return mapper.selectValue(key);
    }
}
