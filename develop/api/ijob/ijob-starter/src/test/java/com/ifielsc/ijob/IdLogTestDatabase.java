// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.sql.Connection;

final class IdLogTestDatabase {
    final boolean oracle;
    final DriverManagerDataSource dataSource;
    final SqlSessionFactory sessions;

    IdLogTestDatabase(Path sqliteFile) throws Exception {
        String url = System.getenv("IJOB_ID_LOG_TEST_URL");
        oracle = url != null;
        if (oracle) {
            String user = System.getenv("IJOB_ID_LOG_TEST_USER");
            if (!"IJOB_ID_LOG_261008".equals(user) || !url.startsWith("jdbc:oracle:thin:")) {
                throw new IllegalStateException(
                        "Only the dedicated ID/log regression schema is allowed");
            }
            dataSource =
                    new DriverManagerDataSource(
                            url, user, System.getenv("IJOB_ID_LOG_TEST_PASSWORD"));
            try (var connection = dataSource.getConnection();
                    var statement = connection.createStatement();
                    var result =
                            statement.executeQuery(
                                    "SELECT sys_context('USERENV','CURRENT_SCHEMA') FROM dual")) {
                if (!result.next() || !user.equals(result.getString(1))) {
                    throw new IllegalStateException("Regression schema mismatch");
                }
            }
        } else {
            dataSource = new DriverManagerDataSource("jdbc:sqlite:" + sqliteFile.toAbsolutePath());
        }
        var config =
                new Configuration(
                        new Environment(
                                "id-log-regression", new JdbcTransactionFactory(), dataSource));
        config.setDatabaseId(oracle ? "oracle" : "sqlite");
        config.setMapUnderscoreToCamelCase(true);
        config.setDefaultStatementTimeout(10);
        for (String resource :
                new String[] {
                    "mapper/runtime/InstanceLogMapper.xml",
                    "mapper/coordination/IdSegmentMapper.xml"
                }) {
            try (var input = new ClassPathResource(resource).getInputStream()) {
                new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
            }
        }
        sessions = new SqlSessionFactoryBuilder().build(config);
    }

    void execute(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
