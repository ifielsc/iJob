// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableScheduling
@MapperScan(basePackages = "com.ifielsc.ijob.infra.mapper")
@ConfigurationPropertiesScan(basePackages = "com.ifielsc.ijob")
@SpringBootApplication(scanBasePackages = "com.ifielsc.ijob")
public class IJobApplication {
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(IJobApplication.class, args);
    }
}
