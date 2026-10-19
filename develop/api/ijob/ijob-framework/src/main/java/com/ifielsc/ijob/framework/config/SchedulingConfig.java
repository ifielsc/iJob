package com.ifielsc.ijob.framework.config;

import com.ifielsc.ijob.common.config.AppProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
public class SchedulingConfig {
    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler(AppProperties appProperties) {
        AppProperties.Scheduling properties = appProperties.getScheduling();
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.getPoolSize());
        scheduler.setThreadNamePrefix("ijob-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationMillis(properties.getShutdownTimeout().toMillis());
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
