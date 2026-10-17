package com.ifelio.ijob;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.ifelio.ijob.infra.coordination.id.IdGenerator;
import com.ifelio.ijob.infra.coordination.work.DatabaseTriggerWorkQueue;
import com.ifelio.ijob.infra.mybatis.AutoIdInterceptor;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

class IJobApplicationTest {
    @Test
    void applicationShouldDeclareAllAssemblyBoundaries() {
        SpringBootApplication application =
                IJobApplication.class.getAnnotation(SpringBootApplication.class);
        ConfigurationPropertiesScan propertiesScan =
                IJobApplication.class.getAnnotation(ConfigurationPropertiesScan.class);
        MapperScan mapperScan = IJobApplication.class.getAnnotation(MapperScan.class);

        assertNotNull(application);
        assertNotNull(propertiesScan);
        assertNotNull(mapperScan);
        assertNotNull(IJobApplication.class.getAnnotation(EnableScheduling.class));
        assertArrayEquals(new String[] {"com.ifelio.ijob"}, application.scanBasePackages());
        assertArrayEquals(new String[] {"com.ifelio.ijob"}, propertiesScan.basePackages());
        assertArrayEquals(new String[] {"com.ifelio.ijob.infra.mapper"}, mapperScan.basePackages());
    }

    @Test
    void developmentContextShouldAssembleCoordinationFoundationWithoutDatabaseAccess() {
        try (var context =
                new SpringApplicationBuilder(IJobApplication.class)
                        .web(WebApplicationType.NONE)
                        .run(
                                "--spring.flyway.enabled=false",
                                "--spring.datasource.hikari.minimum-idle=0",
                                "--app.runtime.mode=DEVELOPMENT",
                                "--app.auth.mode=NONE",
                                "--app.schedule.enabled=false",
                                "--app.event.recovery.enabled=false",
                                "--app.retention.enabled=false",
                                "--logging.level.root=OFF")) {
            assertNotNull(context.getBean(AutoIdInterceptor.class));
            assertNotNull(context.getBean(IdGenerator.class));
            assertNotNull(context.getBean(DatabaseTriggerWorkQueue.class));
        }
    }
}
