package com.ifelio.ijob;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableScheduling
@MapperScan(basePackages = "com.ifelio.ijob.infra.mapper")
@ConfigurationPropertiesScan(basePackages = "com.ifelio.ijob")
@SpringBootApplication(scanBasePackages = "com.ifelio.ijob")
public class IJobApplication {
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(IJobApplication.class, args);
    }
}
