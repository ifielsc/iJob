package com.ifielsc.ijob.monitor.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;

@Configuration
public class OshiConfiguration {

    @Bean
    public SystemInfo systemInfo() {
        return new SystemInfo();
    }

    @Bean
    public HardwareAbstractionLayer hardware(SystemInfo systemInfo) {
        return systemInfo.getHardware();
    }

    @Bean
    public OperatingSystem operatingSystem(SystemInfo systemInfo) {
        return systemInfo.getOperatingSystem();
    }
}
