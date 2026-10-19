package com.ifielsc.ijob.framework.config;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.PlatformDatabaseVendor;
import com.ifielsc.ijob.framework.database.typehandler.SqliteLocalDateTimeTypeHandler;
import com.ifielsc.ijob.framework.database.typehandler.SqliteLocalDateTypeHandler;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

class MyBatisConfigTest {
    @Test
    void shouldCreateDefaultTemplateFromFactory() {
        SqlSessionFactory factory = sqlSessionFactory();
        MyBatisConfig config = new MyBatisConfig();

        SqlSessionTemplate defaultTemplate =
                config.sqlSessionTemplate(
                        factory, config.sqlConflictClassifier(new AppProperties()));
        assertSame(factory, defaultTemplate.getSqlSessionFactory());
    }

    @Test
    void sqliteTemporalHandlersShouldBeScopedToThePlatformConfiguration() {
        AppProperties properties = new AppProperties();
        properties.getPlatformDatabase().setVendor(PlatformDatabaseVendor.SQLITE);
        Configuration configuration = new Configuration();

        new MyBatisConfig().platformTypeHandlerCustomizer(properties).customize(configuration);

        assertTrue(
                configuration.getTypeHandlerRegistry().getTypeHandler(LocalDate.class)
                        instanceof SqliteLocalDateTypeHandler);
        assertTrue(
                configuration.getTypeHandlerRegistry().getTypeHandler(LocalDateTime.class)
                        instanceof SqliteLocalDateTimeTypeHandler);
    }

    private SqlSessionFactory sqlSessionFactory() {
        Environment environment =
                new Environment("test", new JdbcTransactionFactory(), new NoConnectionDataSource());
        Configuration configuration = new Configuration(environment);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static class NoConnectionDataSource extends AbstractDataSource {
        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("Database access is not allowed in this test");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("Database access is not allowed in this test");
        }
    }
}
