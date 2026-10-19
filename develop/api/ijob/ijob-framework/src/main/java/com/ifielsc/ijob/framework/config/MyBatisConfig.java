package com.ifielsc.ijob.framework.config;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.PlatformDatabaseVendor;
import com.ifielsc.ijob.framework.database.PlatformDatabaseResolver;
import com.ifielsc.ijob.framework.database.SqlConflictClassifier;
import com.ifielsc.ijob.framework.database.typehandler.SqliteLocalDateTimeTypeHandler;
import com.ifielsc.ijob.framework.database.typehandler.SqliteLocalDateTypeHandler;

import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.MyBatisExceptionTranslator;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Configuration(proxyBeanMethods = false)
public class MyBatisConfig {
    @Bean
    public DatabaseIdProvider databaseIdProvider(PlatformDatabaseResolver databaseResolver) {
        return databaseResolver::databaseId;
    }

    @Bean
    public ConfigurationCustomizer platformTypeHandlerCustomizer(AppProperties appProperties) {
        return configuration -> {
            if (appProperties.getPlatformDatabase().getVendor() == PlatformDatabaseVendor.SQLITE) {
                configuration
                        .getTypeHandlerRegistry()
                        .register(LocalDate.class, new SqliteLocalDateTypeHandler());
                configuration
                        .getTypeHandlerRegistry()
                        .register(LocalDateTime.class, new SqliteLocalDateTimeTypeHandler());
            }
        };
    }

    @Bean
    public SqlConflictClassifier sqlConflictClassifier(AppProperties appProperties) {
        return new SqlConflictClassifier(appProperties.getPlatformDatabase().getVendor());
    }

    @Bean
    @Primary
    public SqlSessionTemplate sqlSessionTemplate(
            SqlSessionFactory sqlSessionFactory, SqlConflictClassifier conflictClassifier) {
        return new SqlSessionTemplate(
                sqlSessionFactory,
                sqlSessionFactory.getConfiguration().getDefaultExecutorType(),
                new MyBatisExceptionTranslator(() -> conflictClassifier, true));
    }
}
