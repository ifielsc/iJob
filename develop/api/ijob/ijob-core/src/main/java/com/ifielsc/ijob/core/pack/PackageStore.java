// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.core.pack;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.core.security.AesGcmConfigProtector;
import com.ifielsc.ijob.infra.mapper.coordination.ClusterStateMapper;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Durable namespace in the existing V1 state table, independent of session-store mode. */
@Component
public class PackageStore {
    private static final String PREFIX = "ijob:package:v1:";
    private final ClusterStateMapper mapper;
    private final TransactionTemplate transaction;
    private final AesGcmConfigProtector cipher;

    public PackageStore(
            ClusterStateMapper mapper,
            PlatformTransactionManager manager,
            AppProperties properties) {
        this.mapper = mapper;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(30);
        cipher = new AesGcmConfigProtector(properties, "ifj_package.plan:v1");
    }

    public String identity(String kind, String id) {
        String key = PREFIX + "identity:" + kind + ":" + id;
        mapper.insertIfAbsent(key, UUID.randomUUID().toString());
        return mapper.selectValue(key);
    }

    public String get(String key) {
        return mapper.selectValue(PREFIX + key);
    }

    public void put(String key, String value) {
        mapper.upsert(PREFIX + key, value, -1);
    }

    public void secret(String key, String value) {
        mapper.upsert(PREFIX + key, cipher.encrypt(value), 86400);
    }

    public String secret(String key) {
        String value = get(key);
        return value == null ? null : cipher.decrypt(value);
    }

    public List<String> records() {
        var result = new LinkedHashSet<String>();
        // UUID ordering is only a storage cursor, never a recency or ownership filter.
        for (int offset = 0; ; offset += 100) {
            List<String> page = mapper.searchKeys(PREFIX + "record:", "", offset, 100, true);
            page.forEach(key -> result.add(key.substring(PREFIX.length())));
            if (page.size() < 100) return List.copyOf(result);
        }
    }

    public <T> T locked(Supplier<T> operation) {
        return transaction.execute(
                status -> {
                    // Lock acquisition, object publication, identity map and receipt share one
                    // transaction.
                    mapper.insertIfAbsent(PREFIX + "write-lock", "1");
                    mapper.updateValue(PREFIX + "write-lock", "1");
                    return operation.get();
                });
    }
}
