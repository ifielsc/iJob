// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.db;

import cn.hutool.json.JSONUtil;

import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.core.resource.factory.ResourceClientFactory;
import com.ifielsc.ijob.infra.entity.resource.Resource;
import com.zaxxer.hikari.HikariDataSource;

import org.springframework.stereotype.Component;

import java.sql.Connection;

@Component
public class DbResourceFactory implements ResourceClientFactory {
    @Override
    public ResourceType supportType() {
        return ResourceType.DB;
    }

    @Override
    public Object create(Resource resource) {
        DbResourceConfig config = JSONUtil.toBean(resource.getConfig(), DbResourceConfig.class);

        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName(config.getDbType().getDriverClassName());
        ds.setJdbcUrl(config.getJdbcUrl());
        ds.setUsername(config.getUsername());
        ds.setPassword(config.getPassword());
        ds.setSchema(config.getSchema());

        return ds;
    }

    @Override
    public void verifyConnection(Object client) throws Exception {
        try (Connection connection = ((HikariDataSource) client).getConnection()) {
            if (connection.isClosed()) {
                throw new IllegalStateException("database connection unavailable");
            }
        }
    }
}
