package com.ifielsc.ijob.api.config;

import cn.dev33.satoken.dao.auto.SaTokenDaoByObjectFollowString;

import com.ifielsc.ijob.common.cluster.ClusterStateStore;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.runtime", name = "mode", havingValue = "CLUSTER")
public class DatabaseSaTokenDao implements SaTokenDaoByObjectFollowString {
    private final ClusterStateStore stateStore;

    public DatabaseSaTokenDao(ClusterStateStore stateStore) {
        this.stateStore = stateStore;
    }

    @Override
    public String get(String key) {
        return stateStore.get(key);
    }

    @Override
    public void set(String key, String value, long timeout) {
        stateStore.set(key, value, timeout);
    }

    @Override
    public void update(String key, String value) {
        stateStore.update(key, value);
    }

    @Override
    public void delete(String key) {
        stateStore.delete(key);
    }

    @Override
    public long getTimeout(String key) {
        return stateStore.getTimeout(key);
    }

    @Override
    public void updateTimeout(String key, long timeout) {
        stateStore.updateTimeout(key, timeout);
    }

    @Override
    public List<String> searchData(
            String prefix, String keyword, int start, int size, boolean sortType) {
        return stateStore.search(prefix, keyword, start, size, sortType);
    }
}
